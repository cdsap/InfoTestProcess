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
        val workers = processes.map { (pid, proc) -> ReportJson.workerInfo(proc, runtimeStats[pid], pid) }
        val doc = OutputDocument(
            summary = Aggregator.summary(workers, stats),
            byTask = Aggregator.byTask(workers),
            workers = workers,
            tags = Aggregator.tags(workers, stats)
        )
        outputJson.writeText(ReportJson.prettyJson.encodeToString(OutputDocument.serializer(), doc))
    }
}
