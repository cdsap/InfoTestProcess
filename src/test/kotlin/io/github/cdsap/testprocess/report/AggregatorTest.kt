package io.github.cdsap.testprocess.report

import io.github.cdsap.testprocess.agent.WorkerRuntimeStats
import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import org.junit.Test

class AggregatorTest {

    private fun worker(
        pid: Long,
        task: String,
        cpuTimeSec: Double = 1.0,
        cpuCoresAvg: Double = 1.0,
        heapPeakGb: Double = 0.1,
        jitSec: Double = 0.1,
        xmx: String = "512m",
        statsSnapshotMissing: Boolean = false
    ) = WorkerProcessInfo(
        pid = pid, task = task, executor = "E", xmx = xmx,
        uptimeMin = 0.05, cpuTimeSec = cpuTimeSec, cpuCoresAvg = cpuCoresAvg,
        heapUsageGb = heapPeakGb, heapPeakGb = heapPeakGb, metaspacePeakMb = 10.0,
        gcType = "G1", gcCollections = 1L, gcTimeSec = 0.0,
        jitSec = jitSec, classesLoaded = 3000L, peakThreads = 9,
        statsSnapshotMissing = statsSnapshotMissing
    )

    @Test
    fun reportDocumentFromComputesWorkersSummaryByTaskAndTags() {
        val processes = mapOf(
            10L to TestProcess(task = ":a:test", executor = "E1", max = "512m"),
            20L to TestProcess(task = ":a:test", executor = "E2", max = "512m"),
            30L to TestProcess(task = ":b:test", executor = "E3", max = "1g")
        )
        // Worker 10: hot CPU → cpu-heavy tag; worker 20: missing snapshot; worker 30: live, cooler.
        val runtimeStats = mapOf(
            10L to WorkerRuntimeStats(
                pid = 10L,
                uptimeMs = 10_000,
                cpuTimeMs = 50_000, // 5.0 cores avg
                usedHeapBytes = 100_000_000,
                peakHeapBytes = 200_000_000,
                peakMetaspaceBytes = 50_000_000,
                maxHeapBytes = 536_870_912,
                gcCollections = 4,
                gcTimeMs = 120,
                gcType = "G1",
                jitTimeMs = 850,
                classesLoaded = 4_823,
                peakThreads = 18
            ),
            30L to WorkerRuntimeStats(
                pid = 30L,
                uptimeMs = 20_000,
                cpuTimeMs = 2_000, // 0.1 cores avg
                usedHeapBytes = 50_000_000,
                peakHeapBytes = 80_000_000,
                peakMetaspaceBytes = 20_000_000,
                maxHeapBytes = 1_073_741_824,
                gcCollections = 1,
                gcTimeMs = 10,
                gcType = "G1",
                jitTimeMs = 100,
                classesLoaded = 1_000,
                peakThreads = 8
            )
        )
        val stats = Stats(statsSnapshotsMissing = 1)

        val report = ReportDocument.from(processes, runtimeStats, stats)

        assert(report.workers.size == 3)
        assert(report.workers.map { it.pid }.toSet() == setOf(10L, 20L, 30L))
        val missing = report.workers.single { it.pid == 20L }
        assert(missing.statsSnapshotMissing)
        assert(missing.task == ":a:test")

        assert(report.summary.workers.count == 3)
        assert(report.summary.workers.tasksWith == 2)
        assert(report.summary.workers.snapshotsMissing == 1)
        assert(report.summary.cpuCoresAvgMax == 5.0)

        // byTask only includes workers with a live stats snapshot
        assert(report.byTask.keys == setOf(":a:test", ":b:test"))
        assert(report.byTask[":a:test"]!!.workers == 1)
        assert(report.byTask[":b:test"]!!.workers == 1)

        assert(report.tags.contains("tests:cpu-heavy"))
        assert(report.tags.contains("tests:no-snapshot"))
    }

    @Test
    fun summaryComputesMaxesAndSums() {
        val workers = listOf(
            worker(1, ":a:test", cpuTimeSec = 2.0, cpuCoresAvg = 0.5, heapPeakGb = 0.1, jitSec = 0.5),
            worker(2, ":a:test", cpuTimeSec = 3.0, cpuCoresAvg = 4.5, heapPeakGb = 0.4, jitSec = 1.5),
            worker(3, ":b:test", cpuTimeSec = 1.0, cpuCoresAvg = 0.2, heapPeakGb = 0.05, jitSec = 0.2)
        )
        val s = Aggregator.summary(workers, Stats())
        assert(s.workers.count == 3)
        assert(s.workers.tasksWith == 2)
        assert(s.cpuTimeSecSum == 6.0)
        assert(s.cpuCoresAvgMax == 4.5)
        assert(s.heapPeakGbMax == 0.4)
        assert(s.jitSecMax == 1.5)
        assert(s.jitSecSum == 2.2)
    }

    @Test
    fun byTaskGroupsCorrectly() {
        val workers = listOf(
            worker(1, ":a:test", cpuTimeSec = 1.0),
            worker(2, ":a:test", cpuTimeSec = 2.0),
            worker(3, ":b:test", cpuTimeSec = 3.0)
        )
        val map = Aggregator.byTask(workers)
        assert(map[":a:test"]!!.workers == 2)
        assert(map[":a:test"]!!.cpuTimeSecSum == 3.0)
        assert(map[":b:test"]!!.workers == 1)
    }

    @Test
    fun tagsCpuHeavyFiresAtThreshold() {
        val hot = worker(1, ":a:test", cpuCoresAvg = 4.5)
        assert(Aggregator.tags(listOf(hot), Stats()).contains("tests:cpu-heavy"))
        val cool = worker(1, ":a:test", cpuCoresAvg = 3.5)
        assert(!Aggregator.tags(listOf(cool), Stats()).contains("tests:cpu-heavy"))
    }

    @Test
    fun tagsNearOomComparesHeapAgainstParsedXmx() {
        // 0.45 GB peak with 512m (0.5 GB) xmx → 0.9 ratio → near OOM
        val w = worker(1, ":a:test", xmx = "512m", heapPeakGb = 0.45)
        assert(Aggregator.tags(listOf(w), Stats()).contains("tests:near-oom"))
        // 0.1 GB with 1g xmx → 0.1 ratio → not near OOM
        val w2 = worker(1, ":a:test", xmx = "1g", heapPeakGb = 0.1)
        assert(!Aggregator.tags(listOf(w2), Stats()).contains("tests:near-oom"))
    }

    @Test
    fun tagsJitBoundRequiresBothFractionAndMinJit() {
        // jit 1.5s of 2.0s CPU → 75% jit → jit-bound
        val bound = worker(1, ":a:test", cpuTimeSec = 2.0, jitSec = 1.5)
        assert(Aggregator.tags(listOf(bound), Stats()).contains("tests:jit-bound"))
        // jit 0.5s of 1.0s CPU → 50% but below min-jit-sec threshold → NOT tagged
        val tooShort = worker(1, ":a:test", cpuTimeSec = 1.0, jitSec = 0.5)
        assert(!Aggregator.tags(listOf(tooShort), Stats()).contains("tests:jit-bound"))
    }

    @Test
    fun tagsNoSnapshotReflectsStatsField() {
        assert(Aggregator.tags(emptyList(), Stats(statsSnapshotsMissing = 2)).contains("tests:no-snapshot"))
        assert(!Aggregator.tags(emptyList(), Stats(statsSnapshotsMissing = 0)).contains("tests:no-snapshot"))
    }

    @Test
    fun parseXmxHandlesCommonForms() {
        assert(Aggregator.parseXmxGb("512m") == 0.5)
        assert(Aggregator.parseXmxGb("1g") == 1.0)
        assert(Aggregator.parseXmxGb("2G") == 2.0)
        assert(Aggregator.parseXmxGb("1048576k") == 1.0)
        assert(Aggregator.parseXmxGb("") == null)
        assert(Aggregator.parseXmxGb("garbage") == null)
    }

    @Test
    fun trimToBudgetReturnsFullEncodingWhenUnderLimit() {
        val items = (1..5).map { "item-$it" }
        val (json, shown) = trimToBudget(
            items,
            encode = { it.joinToString(",") },
            empty = ""
        )
        assert(shown == 5)
        assert(json == "item-1,item-2,item-3,item-4,item-5")
    }

    @Test
    fun trimToBudgetDropsTailUntilFits() {
        // Encode each item as ~10,000-char string of 'x'. 9 items = 90,000 chars (fits),
        // 10 items = 100,000 chars (exceeds VALUE_LIMIT_SAFE_CHARS by 10,000).
        val chunk = "x".repeat(10_000)
        val items = List(20) { chunk }
        val (json, shown) = trimToBudget(
            items,
            encode = { it.joinToString("") },
            empty = ""
        )
        assert(shown == 9) { "expected 9 items to fit but got $shown" }
        assert(json.length == 90_000)
    }

    @Test
    fun trimToBudgetReturnsEmptyWhenSingleItemTooLarge() {
        val giant = "x".repeat(VALUE_LIMIT_SAFE_CHARS + 1)
        val (json, shown) = trimToBudget(
            listOf(giant),
            encode = { it.joinToString("") },
            empty = ""
        )
        assert(shown == 0)
        assert(json == "")
    }

    @Test
    fun perPidCapIsBelowDevelocityUniqueValueLimit() {
        // Develocity's per-build unique-value limit is 1,000. Our cap must leave
        // room for: 12 flat scalars + up to 3 worker diagnostic keys (.shown /
        // .truncated / .total) = ~15 fixed.
        assert(WORKERS_PER_PID_CAP <= 1000 - 15) {
            "WORKERS_PER_PID_CAP=$WORKERS_PER_PID_CAP leaves too little headroom under the 1,000 per-build value limit"
        }
    }
}
