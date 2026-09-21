package io.github.cdsap.testprocess.report

import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.model.WorkerRuntimeStats
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GbosReportTest {
    @Rule
    @JvmField
    val tmp = TemporaryFolder()

    private val report = ReportDocument.from(
        processes = mapOf(
            13402L to TestProcess(task = ":core:test", executor = "Gradle Test Executor 5", max = "512m")
        ),
        runtimeStats = mapOf(
            13402L to WorkerRuntimeStats(
                pid = 13402L,
                uptimeMs = 1_200,
                cpuTimeMs = 3_050,
                usedHeapBytes = 100_000_000,
                peakHeapBytes = 150_323_855,
                peakMetaspaceBytes = 15_246_295,
                maxHeapBytes = 536_870_912,
                gcCollections = 2,
                gcTimeMs = 0,
                gcType = "G1",
                jitTimeMs = 2_280,
                classesLoaded = 3_503,
                peakThreads = 10
            )
        ),
        stats = Stats()
    )

    @Test
    fun createsGbosReportEnvelopeFromExistingSnapshots() {
        val document = GbosReport.from(report)!!

        assert(document.schemaVersion == "1.0.0")
        assert(document.resource["build.tool.name"]!!.jsonPrimitive.content == "gradle")
        assert(document.observations.size == 2)

        val entity = document.observations.single { it["aggregationScope"]!!.jsonPrimitive.content == "entity" }
        assert(entity["attributes"]!!.jsonObject["process.pid"]!!.jsonPrimitive.intOrNull == 13402)
        assert(entity.measurement("jvm.process.memory.heap.limit", "last") == 536_870_912.0)
        assert(entity.measurement("jvm.process.uptime", "last") == 1.2)
        assert(entity.measurement("jvm.process.gc.collections", "count") == 2.0)

        val build = document.observations.single { it["aggregationScope"]!!.jsonPrimitive.content == "build" }
        assert(build["attributes"]!!.jsonObject.keys == setOf("jvm.process.role"))
        assert(build.measurement("jvm.process.cpu.time", "sum") == 3.05)
        assert(build.measurement("jvm.process.memory.heap.peak", "max") == 150_323_855.0)
    }

    @Test
    fun writesJsonEnvelopeAndCompactNdjsonWhenEnabled() {
        val json = File(tmp.root, "gbos.json")
        val ndjson = File(tmp.root, "gbos.ndjson")

        GbosOutputReport(json, ndjson).write(report, writeJson = true, writeNdjson = true)

        val schema = GbosContract.load()
        val jsonDocument = ReportJson.json.parseToJsonElement(json.readText()).jsonObject
        assert(jsonDocument["schemaVersion"]!!.jsonPrimitive.content == "1.0.0")
        assert(jsonDocument["observations"]!!.jsonArray.size == 2)
        schema.validateReport(jsonDocument)

        val lines = ndjson.readLines()
        assert(lines.size == 2)
        assert(lines.all { "\n" !in it })
        lines.forEachIndexed { index, line ->
            val observation = ReportJson.json.parseToJsonElement(line).jsonObject
            assert(observation["schemaVersion"]!!.jsonPrimitive.content == "1.0.0")
            schema.validateObservation(observation, "ndjson[$index]")
        }
    }

    @Test
    fun skipsDisabledOutputFiles() {
        val json = File(tmp.root, "gbos.json")
        val ndjson = File(tmp.root, "gbos.ndjson")
        json.writeText("stale")
        ndjson.writeText("stale")

        GbosOutputReport(json, ndjson).write(report, writeJson = false, writeNdjson = false)

        assert(!json.exists())
        assert(!ndjson.exists())
    }

    @Test
    fun removesStaleEnabledOutputsWhenThereAreNoObservations() {
        val json = File(tmp.root, "gbos.json")
        val ndjson = File(tmp.root, "gbos.ndjson")
        json.writeText("stale")
        ndjson.writeText("stale")

        GbosOutputReport(json, ndjson).write(
            ReportDocument.from(emptyMap(), emptyMap(), Stats()),
            writeJson = true,
            writeNdjson = true
        )

        assert(!json.exists())
        assert(!ndjson.exists())
    }

    @Test
    fun ordersEntityObservationsByCpuThenPid() {
        val multi = ReportDocument.from(
            processes = mapOf(
                20L to TestProcess(task = ":a:test", executor = "Gradle Test Executor 1", max = "512m"),
                10L to TestProcess(task = ":b:test", executor = "Gradle Test Executor 2", max = "512m"),
                30L to TestProcess(task = ":c:test", executor = "Gradle Test Executor 3", max = "512m")
            ),
            runtimeStats = mapOf(
                20L to runtime(20L, cpuTimeMs = 2_000),
                10L to runtime(10L, cpuTimeMs = 3_000),
                30L to runtime(30L, cpuTimeMs = 3_000)
            ),
            stats = Stats()
        )

        val entityPids = GbosReport.observations(multi)
            .filter { it["aggregationScope"]!!.jsonPrimitive.content == "entity" }
            .map { it["attributes"]!!.jsonObject["process.pid"]!!.jsonPrimitive.intOrNull }

        assert(entityPids == listOf(10, 30, 20))
    }

    @Test
    fun generatedExamplesMatchPublishedPublicSchemaContract() {
        val document = GbosReport.from(report)!!
        val jsonDocument = ReportJson.json.parseToJsonElement(GbosReport.encodeReport(document)).jsonObject
        val schema = GbosContract.load()

        schema.validateReport(jsonDocument)
        document.observations.forEachIndexed { index, observation ->
            schema.validateObservation(observation, "observations[$index]")
        }
        GbosReport.scalarIndexes(document.observations).forEach { (name, value) ->
            schema.validateIndex(name, value)
        }
    }

    private fun runtime(pid: Long, cpuTimeMs: Long) = WorkerRuntimeStats(
        pid = pid,
        uptimeMs = 1_000,
        cpuTimeMs = cpuTimeMs,
        usedHeapBytes = 100_000_000,
        peakHeapBytes = 150_323_855,
        peakMetaspaceBytes = 15_246_295,
        maxHeapBytes = 536_870_912,
        gcCollections = 2,
        gcTimeMs = 0,
        gcType = "G1",
        jitTimeMs = 2_280,
        classesLoaded = 3_503,
        peakThreads = 10
    )

    private fun JsonObject.measurement(name: String, aggregation: String): Double {
        return this["measurements"]!!.jsonArray
            .map { it.jsonObject }
            .single {
                it["name"]!!.jsonPrimitive.content == name &&
                    it["aggregation"]!!.jsonPrimitive.content == aggregation
            }["value"]!!.jsonPrimitive.doubleOrNull!!
    }
}
