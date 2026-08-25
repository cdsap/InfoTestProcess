package io.github.cdsap.testprocess.report

import io.github.cdsap.testprocess.agent.WorkerRuntimeStats
import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class OutputDocument(
    val summary: TestProcessSummary,
    val byTask: Map<String, TaskSummary>,
    val workers: List<WorkerProcessInfo>,
    val tags: List<String>
)

class OutputReport(val outputJson: File) : Report {

    override fun extracted(
        processes: Map<Long, TestProcess>,
        runtimeStats: Map<Long, WorkerRuntimeStats>,
        stats: Stats,
        buildScanData: BuildScanData
    ) {
        if (processes.isEmpty()) {
            outputJson.writeText("{}")
            return
        }
        val report = ReportDocument.from(processes, runtimeStats, stats)
        val doc = OutputDocument(
            summary = report.summary,
            byTask = report.byTask,
            workers = report.workers,
            tags = report.tags
        )
        outputJson.writeText(ReportJson.prettyJson.encodeToString(OutputDocument.serializer(), doc))
    }
}
