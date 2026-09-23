package io.github.cdsap.testprocess.service

import io.github.cdsap.testprocess.model.PersistedState
import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.report.StatsOutputOptions
import kotlinx.serialization.json.Json
import org.gradle.tooling.events.OperationCompletionListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StatsBuildServiceTest {

    @Rule
    @JvmField
    val temp = TemporaryFolder()

    @Test
    fun doesNotImplementUnusedOperationCompletionListener() {
        val serviceType = StatsBuildService::class.java
        assertFalse(
            "StatsBuildService must not implement OperationCompletionListener " +
                "unless registered with BuildEventsListenerRegistry",
            OperationCompletionListener::class.java.isAssignableFrom(serviceType)
        )
        assertTrue(
            "StatsBuildService still uses AutoCloseable.close() as the end-of-build hook",
            AutoCloseable::class.java.isAssignableFrom(serviceType)
        )
        assertFalse(
            "empty onFinish override must not remain after dropping OperationCompletionListener",
            serviceType.declaredMethods.any { it.name == "onFinish" }
        )
    }

    @Test
    fun closeDelegatesPublicationWithoutAssemblingReportDetails() {
        val classBytes = StatsBuildService::class.java
            .getResourceAsStream("StatsBuildService.class")!!
            .readBytes()
            .toString(Charsets.ISO_8859_1)

        assertTrue(
            "close must delegate to StatsReportPublisher",
            classBytes.contains("StatsReportPublisher")
        )
        assertFalse(
            "StatsBuildService must not assemble WorkerStateCollector details",
            classBytes.contains("WorkerStateCollector")
        )
        assertFalse(
            "StatsBuildService must not assemble ReportDocument details",
            classBytes.contains("ReportDocument")
        )
        assertFalse(
            "StatsBuildService must not call StatsOutputWriter directly",
            classBytes.contains("StatsOutputWriter")
        )
        assertTrue(
            "StatsReportPublisher remains the independently testable collaborator",
            StatsReportPublisher::class.java.methods.any { it.name == "publish" }
        )
    }

    @Test
    fun publisherWritesEmptySnapshotWhenRegistryIsEmpty() {
        val registry = temp.newFolder("workers-empty")
        val outs = outputFiles("empty")

        StatsReportPublisher().publish(
            registryDir = registry,
            processes = emptyMap(),
            stats = Stats(),
            options = outs.options(develocity = false, writeGbos = false)
        )

        assertTrue(outs.json.exists())
        assertFalse(outs.txt.exists())
        assertEquals("{}", outs.json.readText())
        assertFalse(outs.gbosJson.exists())
        assertFalse(outs.gbosNdjson.exists())
    }

    @Test
    fun publisherCountsMissingWorkerSnapshots() {
        val registry = temp.newFolder("workers-missing")
        File(registry, "10.json").writeText(
            """{"pid":10,"task":":app:test","executor":"Gradle Test Executor 1","maxHeapBytes":536870912,"startMs":1,"args":[]}"""
        )
        File(registry, "20.json").writeText(
            """{"pid":20,"task":":lib:test","executor":"Gradle Test Executor 2","maxHeapBytes":1073741824,"startMs":2,"args":[]}"""
        )
        File(registry, "10.stats.json").writeText(
            """{"pid":10,"uptimeMs":12000,"cpuTimeMs":3400,"usedHeapBytes":104857600,"peakHeapBytes":209715200,"peakMetaspaceBytes":52428800,"maxHeapBytes":536870912,"gcCollections":4,"gcTimeMs":120,"gcType":"G1","jitTimeMs":850,"classesLoaded":4823,"peakThreads":18}"""
        )
        val outs = outputFiles("missing")

        StatsReportPublisher().publish(
            registryDir = registry,
            processes = emptyMap(),
            stats = Stats(totalProcesses = 2),
            options = outs.options(develocity = true, writeGbos = false)
        )

        assertTrue(outs.txt.exists())
        assertFalse(outs.json.exists())
        val decoded = Json.decodeFromString(PersistedState.serializer(), outs.txt.readText())
        assertEquals(2, decoded.processes.size)
        assertEquals(1, decoded.runtimeStats.size)
        assertEquals(1, decoded.stats.statsSnapshotsCaptured)
        assertEquals(1, decoded.stats.statsSnapshotsMissing)
        assertEquals(":app:test", decoded.processes[10L]?.task)
        assertEquals(":lib:test", decoded.processes[20L]?.task)
    }

    @Test
    fun publisherEnabledGbosWritesJsonAndNdjson() {
        val registry = temp.newFolder("workers-gbos-on")
        File(registry, "10.json").writeText(
            """{"pid":10,"task":":a:test","executor":"E1","maxHeapBytes":536870912,"startMs":1,"args":[]}"""
        )
        File(registry, "10.stats.json").writeText(
            """{"pid":10,"uptimeMs":60000,"cpuTimeMs":30000,"usedHeapBytes":100000000,"peakHeapBytes":200000000,"peakMetaspaceBytes":50000000,"maxHeapBytes":536870912,"gcCollections":2,"gcTimeMs":50,"gcType":"G1","jitTimeMs":400,"classesLoaded":2500,"peakThreads":12}"""
        )
        val outs = outputFiles("gbos-on")

        StatsReportPublisher().publish(
            registryDir = registry,
            processes = emptyMap(),
            stats = Stats(totalProcesses = 1),
            options = outs.options(develocity = false, writeGbos = true)
        )

        assertTrue(outs.json.exists())
        assertTrue(outs.gbosJson.exists())
        assertTrue(outs.gbosNdjson.exists())
        assertTrue(outs.gbosJson.readText().contains("\"observations\""))
        assertTrue(outs.gbosNdjson.readLines().isNotEmpty())
    }

    @Test
    fun publisherDisabledGbosDeletesStaleFiles() {
        val registry = temp.newFolder("workers-gbos-off")
        val outs = outputFiles("gbos-off")
        outs.gbosJson.writeText("stale-json")
        outs.gbosNdjson.writeText("stale-ndjson")

        StatsReportPublisher().publish(
            registryDir = registry,
            processes = emptyMap(),
            stats = Stats(),
            options = outs.options(develocity = false, writeGbos = false)
        )

        assertFalse(outs.gbosJson.exists())
        assertFalse(outs.gbosNdjson.exists())
        assertTrue(outs.json.exists())
    }

    private fun outputFiles(label: String): OutputFiles {
        val dir = temp.newFolder(label)
        return OutputFiles(
            txt = File(dir, "statsTestTasks.txt"),
            json = File(dir, "statsTestTasks.json"),
            gbosJson = File(dir, "gbos.json"),
            gbosNdjson = File(dir, "gbos.ndjson")
        )
    }

    private data class OutputFiles(
        val txt: File,
        val json: File,
        val gbosJson: File,
        val gbosNdjson: File
    ) {
        fun options(develocity: Boolean, writeGbos: Boolean) = StatsOutputOptions(
            develocity = develocity,
            persistedTxt = txt,
            persistedJson = json,
            gbosJson = gbosJson,
            gbosNdjson = gbosNdjson,
            writeGbosJson = writeGbos,
            writeGbosNdjson = writeGbos
        )
    }
}
