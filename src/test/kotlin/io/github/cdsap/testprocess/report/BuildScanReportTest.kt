package io.github.cdsap.testprocess.report

import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.model.WorkerRuntimeStats
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class BuildScanReportTest {
    private val processes = mapOf(
        13402L to TestProcess(task = ":core:test", executor = "Gradle Test Executor 5", max = "512m")
    )
    private val runtimeStats = mapOf(
        13402L to WorkerRuntimeStats(
            pid = 13402L,
            uptimeMs = 120_000,
            cpuTimeMs = 3_050,
            usedHeapBytes = 100_000_000,
            peakHeapBytes = 150_323_855,
            peakMetaspaceBytes = 50_331_648,
            maxHeapBytes = 536_870_912,
            gcCollections = 2,
            gcTimeMs = 50,
            gcType = "G1",
            jitTimeMs = 1_250,
            classesLoaded = 3_503,
            peakThreads = 10
        )
    )

    @Test
    fun gbosDevelocityProjectionIsDisabledByDefault() {
        val scanData = RecordingBuildScanData()

        BuildScanReport().extracted(processes, runtimeStats, Stats(), scanData)

        assert(scanData.values.any { it.first == "testProcess.cpuTimeSec.sum" })
        assert(scanData.values.none { it.first == "gbos.v1.observation" })
        assert(scanData.values.none { it.first.startsWith("gbos.v1.index.") })
    }

    @Test
    fun emitsCanonicalGbosObservationValuesWhenEnabled() {
        val scanData = RecordingBuildScanData()

        BuildScanReport(publishGbos = true).extracted(processes, runtimeStats, Stats(), scanData)

        val observations = scanData.values
            .filter { it.first == "gbos.v1.observation" }
            .map { it.second.asJsonObject() }

        assert(observations.size == 2)
        assert(scanData.values.filter { it.first.startsWith("gbos.v1.") }.all { (name, _) ->
            "13402" !in name &&
                ":core:test" !in name &&
                "Gradle Test Executor 5" !in name
        })
        assert(observations.all { it["schemaVersion"]!!.jsonPrimitive.content == "1.0.0" })
        assert(observations.all { it["producer"]!!.jsonObject["name"]!!.jsonPrimitive.content == "info-test-process" })
        assert(observations.all { it["producer"]!!.jsonObject["version"]!!.jsonPrimitive.content == "2.1.0" })
        assert(observations.all { it["scope"]!!.jsonPrimitive.content == "jvm.process" })

        val entity = observations.single { it["aggregationScope"]!!.jsonPrimitive.content == "entity" }
        val entityAttributes = entity["attributes"]!!.jsonObject
        assert(entityAttributes["process.pid"]!!.jsonPrimitive.content == "13402")
        assert(entityAttributes["jvm.process.role"]!!.jsonPrimitive.content == "test-worker")
        assert(entityAttributes["gradle.task.path"]!!.jsonPrimitive.content == ":core:test")
        assert(entityAttributes["gradle.test.executor"]!!.jsonPrimitive.content == "Gradle Test Executor 5")
        assert(entityAttributes["jvm.gc.name"]!!.jsonPrimitive.content == "G1")
        assert(entity.measurement("jvm.process.memory.heap.limit", "last") == 536_870_912.0)
        assert(entity.measurement("jvm.process.cpu.time", "sum") == 3.05)
        assert(entity.measurement("jvm.process.memory.heap.peak", "max") == 150_323_855.0)

        val build = observations.single { it["aggregationScope"]!!.jsonPrimitive.content == "build" }
        assert(build["attributes"]!!.jsonObject.keys == setOf("jvm.process.role"))
        assert(build.measurement("jvm.process.cpu.cores", "max") == 0.03)
        assert(build.measurement("jvm.process.cpu.time", "sum") == 3.05)
        assert(build.measurement("jvm.process.memory.heap.peak", "max") == 150_323_855.0)
    }

    @Test
    fun emitsOnlyAllowlistedScalarIndexesFromBuildLevelMeasurements() {
        val scanData = RecordingBuildScanData()

        BuildScanReport(publishGbos = true).extracted(processes, runtimeStats, Stats(), scanData)

        val indexes = scanData.values
            .filter { it.first.startsWith("gbos.v1.index.") }
            .toMap()

        assert(
            indexes.keys == setOf(
                "gbos.v1.index.info_test_process.jvm.process.cpu.cores.max",
                "gbos.v1.index.info_test_process.jvm.process.cpu.time.sum",
                "gbos.v1.index.info_test_process.jvm.process.memory.heap.peak.max"
            )
        )

        val build = scanData.values
            .filter { it.first == "gbos.v1.observation" }
            .map { it.second.asJsonObject() }
            .single { it["aggregationScope"]!!.jsonPrimitive.content == "build" }

        assert(indexes["gbos.v1.index.info_test_process.jvm.process.cpu.cores.max"]!!.toDouble() == build.measurement("jvm.process.cpu.cores", "max"))
        assert(indexes["gbos.v1.index.info_test_process.jvm.process.cpu.time.sum"]!!.toDouble() == build.measurement("jvm.process.cpu.time", "sum"))
        assert(indexes["gbos.v1.index.info_test_process.jvm.process.memory.heap.peak.max"]!!.toDouble() == build.measurement("jvm.process.memory.heap.peak", "max"))
    }

    @Test
    fun keepsLegacyDevelocityCustomValuesAndTagsWhenGbosIsEnabled() {
        val heavyStats = mapOf(
            13402L to runtimeStats.getValue(13402L).copy(
                cpuTimeMs = 500_000,
                uptimeMs = 100_000
            )
        )
        val scanData = RecordingBuildScanData()

        BuildScanReport(publishGbos = true).extracted(processes, heavyStats, Stats(), scanData)

        assert(scanData.values.any { it.first == "testProcess.cpuTimeSec.sum" })
        assert(scanData.values.any { it.first == "testProcess.worker.13402" })
        assert(scanData.tags == listOf("tests:cpu-heavy"))
        assert(scanData.values.any { it.first == "gbos.v1.observation" })
    }

    @Test
    fun omitsMissingSnapshotSentinelsFromGbosObservations() {
        val scanData = RecordingBuildScanData()

        BuildScanReport(publishGbos = true).extracted(processes, emptyMap(), Stats(statsSnapshotsMissing = 1), scanData)

        val observations = scanData.values
            .filter { it.first == "gbos.v1.observation" }
            .map { it.second.asJsonObject() }
        val entity = observations.single { it["aggregationScope"]!!.jsonPrimitive.content == "entity" }
        val measurementNames = entity["measurements"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }

        assert(measurementNames == listOf("jvm.process.memory.heap.limit"))
        assert(entity.measurement("jvm.process.memory.heap.limit", "last") == 536_870_912.0)
        assert(entity["diagnostics"]!!.jsonArray.single().jsonObject["code"]!!.jsonPrimitive.content == "gbos.test_process.stats_snapshot_missing")
        assert(observations.none { it["aggregationScope"]!!.jsonPrimitive.content == "build" })
        assert(scanData.values.none { it.first.startsWith("gbos.v1.index.") })
        assert("-1" !in entity.toString())
    }

    private fun String.asJsonObject(): JsonObject = ReportJson.json.parseToJsonElement(this).jsonObject

    private fun JsonObject.measurement(name: String, aggregation: String): Double {
        return this["measurements"]!!.jsonArray
            .map { it.jsonObject }
            .single {
                it["name"]!!.jsonPrimitive.content == name &&
                    it["aggregation"]!!.jsonPrimitive.content == aggregation
            }["value"]!!.jsonPrimitive.double
    }

    private class RecordingBuildScanData : BuildScanData {
        val values = mutableListOf<Pair<String, String>>()
        val tags = mutableListOf<String>()

        override fun value(key: String, value: String) {
            values += key to value
        }

        override fun tag(name: String) {
            tags += name
        }
    }
}
