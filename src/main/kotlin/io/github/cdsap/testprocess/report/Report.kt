package io.github.cdsap.testprocess.report

import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.model.WorkerRuntimeStats
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface Report {
    fun extracted(
        processes: Map<Long, TestProcess>,
        runtimeStats: Map<Long, WorkerRuntimeStats>,
        stats: Stats,
        buildScanData: BuildScanData
    )
}

/**
 * Shared report-domain projection from persisted worker state. Output adapters
 * (Build Scan, JSON file) consume this instead of rebuilding workers / summary /
 * byTask / tags themselves.
 */
internal data class ReportDocument(
    val workers: List<WorkerProcessInfo>,
    val summary: TestProcessSummary,
    val byTask: Map<String, TaskSummary>,
    val tags: List<String>
) {
    companion object {
        fun from(
            processes: Map<Long, TestProcess>,
            runtimeStats: Map<Long, WorkerRuntimeStats>,
            stats: Stats
        ): ReportDocument {
            val workers = processes.map { (pid, proc) -> ReportJson.workerInfo(proc, runtimeStats[pid], pid) }
            return ReportDocument(
                workers = workers,
                summary = Aggregator.summary(workers, stats),
                byTask = Aggregator.byTask(workers),
                tags = Aggregator.tags(workers, stats)
            )
        }
    }
}

@Serializable
data class WorkerProcessInfo(
    val pid: Long,
    val task: String,
    val executor: String,
    val xmx: String,
    val uptimeMin: Double,
    val cpuTimeSec: Double,
    val cpuCoresAvg: Double,
    val heapUsageGb: Double,
    val heapPeakGb: Double,
    val metaspacePeakMb: Double,
    val gcType: String,
    val gcCollections: Long,
    val gcTimeSec: Double,
    val jitSec: Double,
    val classesLoaded: Long,
    val peakThreads: Int,
    val statsSnapshotMissing: Boolean = false
)

@Serializable
data class WorkersBreakdown(
    val count: Int,
    val tasksWith: Int,
    val snapshotsMissing: Int
)

@Serializable
data class TestProcessSummary(
    val workers: WorkersBreakdown,
    val cpuCoresAvgMax: Double,
    val cpuTimeSecSum: Double,
    val heapPeakGbMax: Double,
    val metaspacePeakMbMax: Double,
    val jitSecSum: Double,
    val jitSecMax: Double,
    val classesLoadedMax: Long,
    val gcCollectionsSum: Long,
    val uptimeMinSum: Double
)

@Serializable
data class TaskSummary(
    val workers: Int,
    val cpuCoresAvgMax: Double,
    val cpuTimeSecSum: Double,
    val heapPeakGbMax: Double,
    val jitSecMax: Double,
    val classesLoadedMax: Long,
    val peakThreadsMax: Int
)

internal object ReportJson {
    val json = Json { prettyPrint = false; encodeDefaults = true }
    val prettyJson = Json { prettyPrint = true; encodeDefaults = true }

    fun workerInfo(proc: TestProcess, s: WorkerRuntimeStats?, pid: Long): WorkerProcessInfo {
        if (s == null) {
            return WorkerProcessInfo(
                pid = pid,
                task = proc.task,
                executor = proc.executor,
                xmx = proc.max,
                uptimeMin = 0.0,
                cpuTimeSec = 0.0,
                cpuCoresAvg = 0.0,
                heapUsageGb = 0.0,
                heapPeakGb = 0.0,
                metaspacePeakMb = 0.0,
                gcType = "Unknown",
                gcCollections = 0,
                gcTimeSec = 0.0,
                jitSec = 0.0,
                classesLoaded = -1,
                peakThreads = -1,
                statsSnapshotMissing = true
            )
        }
        return WorkerProcessInfo(
            pid = pid,
            task = proc.task,
            executor = proc.executor,
            xmx = proc.max,
            uptimeMin = StatFormat.minutes(s.uptimeMs),
            cpuTimeSec = StatFormat.seconds(s.cpuTimeMs),
            cpuCoresAvg = StatFormat.cores(s.cpuTimeMs, s.uptimeMs),
            heapUsageGb = StatFormat.gigs(s.usedHeapBytes),
            heapPeakGb = StatFormat.gigs(s.peakHeapBytes),
            metaspacePeakMb = StatFormat.megs(s.peakMetaspaceBytes),
            gcType = s.gcType,
            gcCollections = s.gcCollections,
            gcTimeSec = StatFormat.seconds(s.gcTimeMs),
            jitSec = StatFormat.seconds(s.jitTimeMs),
            classesLoaded = s.classesLoaded,
            peakThreads = s.peakThreads
        )
    }
}

/**
 * Threshold for the `tests:cpu-heavy` tag: any worker whose avg core-utilization
 * over its uptime exceeded this. 4.0 means "averaged 4 cores busy".
 */
internal const val CPU_HEAVY_THRESHOLD_CORES = 4.0

/**
 * Threshold for the `tests:near-oom` tag: any worker's peak heap used / configured
 * xmx exceeded this fraction. 0.8 = within 20% of the heap budget.
 */
internal const val NEAR_OOM_THRESHOLD_RATIO = 0.8

/**
 * Threshold for the `tests:jit-bound` tag: any worker spent more than this fraction
 * of its CPU time in JIT compilation, and at least 1s of JIT time. 0.5 = JIT > 50% of CPU.
 */
internal const val JIT_BOUND_THRESHOLD_RATIO = 0.5
internal const val JIT_BOUND_MIN_JIT_SEC = 1.0

/**
 * Develocity custom-value length limit is 100,000 characters. We keep a 10 % safety
 * margin so the encoded JSON for testProcess.byTask is rejected locally before
 * Develocity would reject it remotely.
 */
internal const val VALUE_LIMIT_SAFE_CHARS = 90_000

/**
 * Develocity's unique-custom-value cap is 1,000 per build scan. We leave headroom for
 * the 12 flat scalars + up to 3 worker diagnostic keys (.shown/.truncated/.total),
 * then cap per-PID worker values at this many. Caller pre-sorts by importance
 * (cpuTimeSec desc) so the heaviest workers survive truncation.
 */
internal const val WORKERS_PER_PID_CAP = 950

/**
 * Encode [items] into a JSON string that fits within [VALUE_LIMIT_SAFE_CHARS]. If the
 * full encoding overflows, drops the lowest-priority items (callers pre-sort items
 * highest-priority first) until the result fits. Returns the encoded JSON together
 * with the number of items actually included.
 */
internal fun <T> trimToBudget(
    items: List<T>,
    encode: (List<T>) -> String,
    empty: String
): Pair<String, Int> {
    if (items.isEmpty()) return empty to 0
    var lo = 0
    var hi = items.size
    var bestJson = empty
    var bestN = 0
    // Binary search the largest prefix that still fits.
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        if (mid == 0) {
            bestJson = empty
            bestN = 0
            lo = mid + 1
            continue
        }
        val json = encode(items.take(mid))
        if (json.length <= VALUE_LIMIT_SAFE_CHARS) {
            bestJson = json
            bestN = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return bestJson to bestN
}

object Aggregator {
    fun summary(workers: List<WorkerProcessInfo>, stats: Stats): TestProcessSummary {
        val live = workers.filter { !it.statsSnapshotMissing }
        return TestProcessSummary(
            workers = WorkersBreakdown(
                count = workers.size,
                tasksWith = workers.map { it.task }.distinct().size,
                snapshotsMissing = stats.statsSnapshotsMissing
            ),
            cpuCoresAvgMax = round2(live.maxOfOrNull { it.cpuCoresAvg } ?: 0.0),
            cpuTimeSecSum = round2(live.sumOf { it.cpuTimeSec }),
            heapPeakGbMax = round2(live.maxOfOrNull { it.heapPeakGb } ?: 0.0),
            metaspacePeakMbMax = round2(live.maxOfOrNull { it.metaspacePeakMb } ?: 0.0),
            jitSecSum = round2(live.sumOf { it.jitSec }),
            jitSecMax = round2(live.maxOfOrNull { it.jitSec } ?: 0.0),
            classesLoadedMax = live.maxOfOrNull { it.classesLoaded } ?: 0L,
            gcCollectionsSum = live.sumOf { it.gcCollections },
            uptimeMinSum = round2(live.sumOf { it.uptimeMin })
        )
    }

    fun byTask(workers: List<WorkerProcessInfo>): Map<String, TaskSummary> {
        return workers.filter { !it.statsSnapshotMissing }
            .groupBy { it.task }
            .mapValues { (_, list) ->
                TaskSummary(
                    workers = list.size,
                    cpuCoresAvgMax = round2(list.maxOf { it.cpuCoresAvg }),
                    cpuTimeSecSum = round2(list.sumOf { it.cpuTimeSec }),
                    heapPeakGbMax = round2(list.maxOf { it.heapPeakGb }),
                    jitSecMax = round2(list.maxOf { it.jitSec }),
                    classesLoadedMax = list.maxOf { it.classesLoaded },
                    peakThreadsMax = list.maxOf { it.peakThreads }
                )
            }
    }

    fun tags(workers: List<WorkerProcessInfo>, stats: Stats): List<String> {
        val tags = mutableListOf<String>()
        val live = workers.filter { !it.statsSnapshotMissing }
        if (live.any { it.cpuCoresAvg > CPU_HEAVY_THRESHOLD_CORES }) tags += "tests:cpu-heavy"
        if (live.any { nearOom(it) }) tags += "tests:near-oom"
        if (live.any { jitBound(it) }) tags += "tests:jit-bound"
        if (stats.statsSnapshotsMissing > 0) tags += "tests:no-snapshot"
        return tags
    }

    private fun nearOom(w: WorkerProcessInfo): Boolean {
        val xmxGb = parseXmxGb(w.xmx) ?: return false
        if (xmxGb <= 0.0) return false
        return (w.heapPeakGb / xmxGb) >= NEAR_OOM_THRESHOLD_RATIO
    }

    private fun jitBound(w: WorkerProcessInfo): Boolean {
        if (w.jitSec < JIT_BOUND_MIN_JIT_SEC) return false
        if (w.cpuTimeSec <= 0.0) return false
        return (w.jitSec / w.cpuTimeSec) >= JIT_BOUND_THRESHOLD_RATIO
    }

    /**
     * Parse a JVM-style heap size like "512m", "1g", "2048k", "536870912" into GB.
     * Returns null if unparseable.
     */
    internal fun parseXmxGb(xmx: String): Double? {
        if (xmx.isEmpty()) return null
        val trimmed = xmx.trim().lowercase()
        val (numStr, unit) = when {
            trimmed.endsWith("g") -> trimmed.dropLast(1) to 1024.0 * 1024.0 * 1024.0
            trimmed.endsWith("m") -> trimmed.dropLast(1) to 1024.0 * 1024.0
            trimmed.endsWith("k") -> trimmed.dropLast(1) to 1024.0
            else -> trimmed to 1.0
        }
        val num = numStr.toDoubleOrNull() ?: return null
        return (num * unit) / (1024.0 * 1024.0 * 1024.0)
    }

    private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0
}

internal object StatFormat {
    fun gigs(bytes: Long): Double = round2(bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
    fun megs(bytes: Long): Double = round2(bytes.toDouble() / (1024.0 * 1024.0))
    fun seconds(ms: Long): Double = round2(ms / 1000.0)
    fun minutes(ms: Long): Double = round2(ms / 60_000.0)
    fun cores(cpuTimeMs: Long, uptimeMs: Long): Double {
        if (cpuTimeMs < 0 || uptimeMs <= 0) return 0.0
        return round2(cpuTimeMs.toDouble() / uptimeMs.toDouble())
    }
    private fun round2(v: Double): Double = (Math.round(v * 100.0) / 100.0)
}
