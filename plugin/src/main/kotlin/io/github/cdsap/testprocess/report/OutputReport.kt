package io.github.cdsap.testprocess.report

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class OutputDocument(
    val summary: TestProcessSummary,
    val byTask: Map<String, TaskSummary>,
    val workers: List<WorkerProcessInfo>,
    val tags: List<String>
)

class OutputReport(val outputJson: File) {

    fun write(report: ReportDocument) {
        AtomicFileWriter.replace(outputJson, render(report))
    }

    fun render(report: ReportDocument): String {
        if (report.workers.isEmpty()) {
            return "{}"
        }
        val doc = OutputDocument(
            summary = report.summary,
            byTask = report.byTask,
            workers = report.workers,
            tags = report.tags
        )
        return ReportJson.prettyJson.encodeToString(OutputDocument.serializer(), doc)
    }
}
