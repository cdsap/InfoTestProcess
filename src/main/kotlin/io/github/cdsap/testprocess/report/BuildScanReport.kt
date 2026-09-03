package io.github.cdsap.testprocess.report

import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import io.github.cdsap.testprocess.model.PersistedState
import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.model.WorkerRuntimeStats
import org.gradle.api.provider.Provider

class BuildScanReport(
    private val publishGbos: Boolean = false
) : Report {

    fun develocityBuildScanReporting(
        develocityConfiguration: DevelocityConfiguration,
        provider: Provider<PersistedState>
    ) {
        develocityConfiguration.buildScan {
            val develocityValue = DevelocityValue(this)
            buildFinished {
                if (provider.isPresent) {
                    val state = provider.get()
                    extracted(state.processes, state.runtimeStats, state.stats, develocityValue)
                }
            }
        }
    }

    override fun extracted(
        processes: Map<Long, TestProcess>,
        runtimeStats: Map<Long, WorkerRuntimeStats>,
        stats: Stats,
        buildScanData: BuildScanData
    ) {
        if (processes.isEmpty()) return

        val report = ReportDocument.from(processes, runtimeStats, stats)
        val workers = report.workers
        val summary = report.summary
        val tags = report.tags

        // Flat numeric metrics — DRV indexes these as scalar columns.
        buildScanData.value("testProcess.workers.count", summary.workers.count.toString())
        buildScanData.value("testProcess.workers.tasksWith", summary.workers.tasksWith.toString())
        buildScanData.value("testProcess.workers.snapshotsMissing", summary.workers.snapshotsMissing.toString())
        buildScanData.value("testProcess.cpuCoresAvg.max", summary.cpuCoresAvgMax.toString())
        buildScanData.value("testProcess.cpuTimeSec.sum", summary.cpuTimeSecSum.toString())
        buildScanData.value("testProcess.heapPeakGb.max", summary.heapPeakGbMax.toString())
        buildScanData.value("testProcess.metaspacePeakMb.max", summary.metaspacePeakMbMax.toString())
        buildScanData.value("testProcess.jitSec.sum", summary.jitSecSum.toString())
        buildScanData.value("testProcess.jitSec.max", summary.jitSecMax.toString())
        buildScanData.value("testProcess.classesLoaded.max", summary.classesLoadedMax.toString())
        buildScanData.value("testProcess.gcCollections.sum", summary.gcCollectionsSum.toString())
        buildScanData.value("testProcess.uptimeMin.sum", summary.uptimeMinSum.toString())

        // Per-worker detail — one custom value per PID. Per-task aggregates are
        // intentionally NOT emitted as a separate scan value: they are fully derivable
        // from the per-PID JSONs via DRV `GROUP BY JSON_EXTRACT(value, '$.task')`, so
        // emitting them on the scan would just duplicate data. Scales linearly with worker
        // count up to the per-build unique-value cap (1,000 in Develocity). Each value
        // is a small JSON object (well under the per-value 100,000-char limit), so the
        // binding constraint is COUNT not LENGTH. We sort by cpuTimeSec desc and trim
        // if the cap would be exceeded, preserving the heaviest workers.
        val workersByImportance = workers.sortedByDescending { it.cpuTimeSec }
        val emitted = workersByImportance.take(WORKERS_PER_PID_CAP)
        emitted.forEach { w ->
            buildScanData.value(
                "testProcess.worker.${w.pid}",
                ReportJson.json.encodeToString(WorkerProcessInfo.serializer(), w)
            )
        }
        buildScanData.value("testProcess.worker.shown", emitted.size.toString())
        if (emitted.size < workers.size) {
            buildScanData.value("testProcess.worker.truncated", "true")
            buildScanData.value("testProcess.worker.total", workers.size.toString())
        }

        // Scan tags for fast categorical filtering in DRV.
        tags.forEach { buildScanData.tag(it) }

        if (publishGbos) {
            GbosDevelocityProjection.publish(report, buildScanData)
        }
    }
}
