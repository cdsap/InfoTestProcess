package io.github.cdsap.testprocess.report

import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.model.WorkerRuntimeStats
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OutputReportTest {

    @Rule
    @JvmField
    val temp = TemporaryFolder()

    @Test
    fun emptyProcessesWritesEmptyObject() {
        val out = temp.newFile("statsTestTasks.json")
        OutputReport(out).write(ReportDocument.from(emptyMap(), emptyMap(), Stats()))
        assert(out.readText() == "{}")
    }

    @Test
    fun nonEmptyProcessesWritesOutputDocumentShape() {
        val out = temp.newFile("statsTestTasks.json")
        val processes = mapOf(
            10L to TestProcess(task = ":a:test", executor = "E1", max = "512m")
        )
        val runtimeStats = mapOf(
            10L to WorkerRuntimeStats(
                pid = 10L,
                uptimeMs = 60_000,
                cpuTimeMs = 30_000,
                usedHeapBytes = 100_000_000,
                peakHeapBytes = 200_000_000,
                peakMetaspaceBytes = 50_000_000,
                maxHeapBytes = 536_870_912,
                gcCollections = 2,
                gcTimeMs = 50,
                gcType = "G1",
                jitTimeMs = 400,
                classesLoaded = 2_500,
                peakThreads = 12
            )
        )

        val report = ReportDocument.from(processes, runtimeStats, Stats(totalProcesses = 1))
        OutputReport(out).write(report)

        val body = out.readText()
        assert(body.contains("\"summary\""))
        assert(body.contains("\"byTask\""))
        assert(body.contains("\"workers\""))
        assert(body.contains("\"tags\""))

        val doc = Json.decodeFromString(OutputDocument.serializer(), body)
        assert(doc.workers.size == 1)
        assert(doc.workers.single().pid == 10L)
        assert(doc.workers.single().task == ":a:test")
        assert(doc.summary.workers.count == 1)
        assert(doc.byTask.containsKey(":a:test"))
        assert(doc.summary == report.summary)
        assert(doc.byTask == report.byTask)
        assert(doc.workers == report.workers)
        assert(doc.tags == report.tags)
    }
}
