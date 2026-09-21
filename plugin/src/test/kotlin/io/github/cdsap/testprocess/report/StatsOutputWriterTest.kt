package io.github.cdsap.testprocess.report

import io.github.cdsap.testprocess.model.PersistedState
import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.model.WorkerRuntimeStats
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StatsOutputWriterTest {

    @Rule
    @JvmField
    val temp = TemporaryFolder()

    @Test
    fun develocityModeWritesPersistedTxtAndLeavesJsonUntouched() {
        val txt = File(temp.root, "statsTestTasks.txt")
        val json = File(temp.root, "statsTestTasks.json")
        val gbosJson = File(temp.root, "gbos.json")
        val gbosNdjson = File(temp.root, "gbos.ndjson")
        val legacy = sampleLegacyState()
        val report = ReportDocument.from(legacy.processes, legacy.runtimeStats, legacy.stats)

        StatsOutputWriter().write(
            report,
            legacy,
            options(
                develocity = true,
                persistedTxt = txt,
                persistedJson = json,
                gbosJson = gbosJson,
                gbosNdjson = gbosNdjson
            )
        )

        assertTrue(txt.exists())
        assertFalse(json.exists())
        val decoded = Json.decodeFromString(PersistedState.serializer(), txt.readText())
        assertEquals(legacy, decoded)
    }

    @Test
    fun nonDevelocityModeWritesPersistedJson() {
        val txt = File(temp.root, "statsTestTasks.txt")
        val json = File(temp.root, "nested/statsTestTasks.json")
        val gbosJson = File(temp.root, "gbos.json")
        val gbosNdjson = File(temp.root, "gbos.ndjson")
        val legacy = sampleLegacyState()
        val report = ReportDocument.from(legacy.processes, legacy.runtimeStats, legacy.stats)

        StatsOutputWriter().write(
            report,
            legacy,
            options(
                develocity = false,
                persistedTxt = txt,
                persistedJson = json,
                gbosJson = gbosJson,
                gbosNdjson = gbosNdjson
            )
        )

        assertTrue(json.exists())
        assertFalse(txt.exists())
        val body = json.readText()
        assertTrue(body.contains("\"summary\""))
        assertTrue(body.contains("\"workers\""))
        assertTrue(body.contains(":a:test"))
    }

    @Test
    fun gbosEnabledWritesJsonAndNdjson() {
        val legacy = sampleLegacyState()
        val report = ReportDocument.from(legacy.processes, legacy.runtimeStats, legacy.stats)
        val gbosJson = File(temp.root, "gbos.json")
        val gbosNdjson = File(temp.root, "gbos.ndjson")

        StatsOutputWriter().write(
            report,
            legacy,
            options(
                develocity = false,
                persistedTxt = File(temp.root, "statsTestTasks.txt"),
                persistedJson = File(temp.root, "statsTestTasks.json"),
                gbosJson = gbosJson,
                gbosNdjson = gbosNdjson,
                writeGbosJson = true,
                writeGbosNdjson = true
            )
        )

        assertTrue(gbosJson.exists())
        assertTrue(gbosNdjson.exists())
        assertTrue(gbosJson.readText().contains("\"observations\""))
        assertTrue(gbosNdjson.readLines().isNotEmpty())
    }

    @Test
    fun gbosDisabledDeletesStaleFiles() {
        val gbosJson = File(temp.root, "gbos.json").also { it.writeText("stale-json") }
        val gbosNdjson = File(temp.root, "gbos.ndjson").also { it.writeText("stale-ndjson") }
        val legacy = PersistedState(emptyMap(), emptyMap(), Stats())
        val report = ReportDocument.from(emptyMap(), emptyMap(), Stats())

        StatsOutputWriter().write(
            report,
            legacy,
            options(
                develocity = false,
                persistedTxt = File(temp.root, "statsTestTasks.txt"),
                persistedJson = File(temp.root, "statsTestTasks.json"),
                gbosJson = gbosJson,
                gbosNdjson = gbosNdjson,
                writeGbosJson = false,
                writeGbosNdjson = false
            )
        )

        assertFalse(gbosJson.exists())
        assertFalse(gbosNdjson.exists())
    }

    private fun options(
        develocity: Boolean,
        persistedTxt: File,
        persistedJson: File,
        gbosJson: File,
        gbosNdjson: File,
        writeGbosJson: Boolean = false,
        writeGbosNdjson: Boolean = false
    ) = StatsOutputOptions(
        develocity = develocity,
        persistedTxt = persistedTxt,
        persistedJson = persistedJson,
        gbosJson = gbosJson,
        gbosNdjson = gbosNdjson,
        writeGbosJson = writeGbosJson,
        writeGbosNdjson = writeGbosNdjson
    )

    private fun sampleLegacyState(): PersistedState {
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
        return PersistedState(processes, runtimeStats, Stats(totalProcesses = 1))
    }
}
