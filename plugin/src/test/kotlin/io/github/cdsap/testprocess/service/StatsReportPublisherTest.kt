package io.github.cdsap.testprocess.service

import io.github.cdsap.testprocess.agent.WorkerRegistryEntry
import io.github.cdsap.testprocess.agent.WorkerRegistrySnapshot
import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.WorkerRuntimeStats
import io.github.cdsap.testprocess.report.StatsOutputOptions
import io.github.cdsap.testprocess.report.StatsOutputWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StatsReportPublisherTest {
    @Rule
    @JvmField
    val temp = TemporaryFolder()

    @Test
    fun publishesFromInMemoryWorkerStateWithoutRegistryFiles() {
        val output = File(temp.newFolder("output"), "stats.json")
        val reader = WorkerStateReader {
            assertEquals("unused-registry", it.name)
            WorkerRegistrySnapshot(
                identityEntries = listOf(
                    WorkerRegistryEntry(
                        pid = 42,
                        task = ":app:test",
                        executor = "Gradle Test Executor 1",
                        maxHeapBytes = 536870912,
                        startMs = 1
                    )
                ),
                runtimeStats = listOf(
                    WorkerRuntimeStats(pid = 42, uptimeMs = 1000, usedHeapBytes = 2048)
                )
            )
        }

        StatsReportPublisher(StatsOutputWriter(), reader).publish(
            registryDir = File(temp.root, "unused-registry"),
            processes = emptyMap(),
            stats = Stats(totalProcesses = 1),
            options = StatsOutputOptions(
                develocity = false,
                persistedTxt = File(temp.root, "unused.txt"),
                persistedJson = output,
                gbosJson = File(temp.root, "unused-gbos.json"),
                gbosNdjson = File(temp.root, "unused-gbos.ndjson"),
                writeGbosJson = false,
                writeGbosNdjson = false
            )
        )

        assertTrue(output.exists())
        assertTrue(output.readText().contains(":app:test"))
        assertTrue(output.readText().contains("\"uptimeMin\": 0.02"))
    }
}
