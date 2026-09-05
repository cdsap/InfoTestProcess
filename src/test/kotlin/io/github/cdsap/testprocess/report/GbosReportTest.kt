package io.github.cdsap.testprocess.report

import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.model.WorkerRuntimeStats
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
    fun generatedExamplesMatchBundledPublicSchemaContract() {
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

    private class GbosContract(
        private val reportRequired: Set<String>,
        private val observationRequired: Set<String>,
        private val reportProperties: Set<String>,
        private val observationProperties: Set<String>,
        private val metrics: Map<String, Metric>,
        private val attributes: Map<String, Attribute>,
        private val scopes: Set<String>,
        private val indexes: Map<String, Index>
    ) {
        fun validateReport(report: JsonObject) {
            assert(report.keys.containsAll(reportRequired))
            assert(report.keys.all { it in reportProperties })
            assert(report["schemaVersion"]!!.jsonPrimitive.content == "1.0.0")
            validateAttributes(report["resource"]!!.jsonObject, "resource")
            assert(report["observations"] is JsonArray)
            assert(report["observations"]!!.jsonArray.isNotEmpty())
        }

        fun validateObservation(observation: JsonObject, where: String) {
            assert(observation.keys.containsAll(observationRequired)) { "$where missing required fields" }
            assert(observation.keys.all { it in observationProperties }) { "$where has unknown fields ${observation.keys}" }
            assert(observation["schemaVersion"]!!.jsonPrimitive.content == "1.0.0")
            assert(observation["producer"]!!.jsonObject["name"]!!.jsonPrimitive.content == "info-test-process")
            assert(observation["scope"]!!.jsonPrimitive.content in scopes)
            assert(observation["aggregationScope"]!!.jsonPrimitive.content in setOf("entity", "task", "project", "build"))
            validateAttributes(observation["attributes"]!!.jsonObject, "$where.attributes")

            val measurements = observation["measurements"]!!.jsonArray
            assert(measurements.isNotEmpty())
            val seen = mutableSetOf<Pair<String, String>>()
            measurements.forEachIndexed { index, item ->
                val measurement = item.jsonObject
                val name = measurement["name"]!!.jsonPrimitive.content
                val metric = metrics.getValue(name)
                val aggregation = measurement["aggregation"]!!.jsonPrimitive.content
                assert(measurement.keys == setOf("name", "value", "unit", "aggregation"))
                assert(measurement["unit"]!!.jsonPrimitive.content == metric.unit) { "$where.measurements[$index] unit" }
                assert(aggregation in metric.allowedAggregations) { "$where.measurements[$index] aggregation" }
                assert(measurement["value"]!!.jsonPrimitive.doubleOrNull != null)
                assert(seen.add(name to aggregation)) { "$where duplicate measurement $name/$aggregation" }
            }
        }

        fun validateIndex(name: String, value: String) {
            val index = indexes.getValue(name)
            val number = value.toDoubleOrNull()
            assert(number != null && number.isFinite()) { "index $name must be finite numeric string" }
            assert(index.producer == "info-test-process")
            assert(index.scope in scopes)
            assert(index.metric in metrics)
            assert(index.aggregation in metrics.getValue(index.metric).allowedAggregations)
            assert(index.aggregationScope == "build")
        }

        private fun validateAttributes(actual: JsonObject, where: String) {
            actual.forEach { (name, value) ->
                val attribute = attributes.getValue(name)
                when (attribute.type) {
                    "string" -> assert(value.jsonPrimitive.contentOrNull != null) { "$where.$name must be string" }
                    "integer" -> assert(value.jsonPrimitive.intOrNull != null) { "$where.$name must be integer" }
                    "number" -> assert(value.jsonPrimitive.doubleOrNull != null) { "$where.$name must be number" }
                    "boolean" -> assert(value is JsonPrimitive && value.isString.not()) { "$where.$name must be boolean" }
                }
                if (attribute.values.isNotEmpty()) {
                    assert(value.jsonPrimitive.content in attribute.values) { "$where.$name has invalid value" }
                }
            }
        }

        companion object {
            fun load(): GbosContract {
                val reportSchema = resourceJson("gbos/schema/report.schema.json")
                val observationSchema = resourceJson("gbos/schema/observation.schema.json")
                val conventions = resourceJson("gbos/registry/semantic-conventions.json")
                val develocityIndexes = resourceJson("gbos/registry/develocity-indexes.json")
                return GbosContract(
                    reportRequired = required(reportSchema),
                    observationRequired = required(observationSchema),
                    reportProperties = properties(reportSchema),
                    observationProperties = properties(observationSchema),
                    metrics = conventions["metrics"]!!.jsonArray.associate {
                        val metric = it.jsonObject
                        metric["name"]!!.jsonPrimitive.content to Metric(
                            unit = metric["unit"]!!.jsonPrimitive.content,
                            allowedAggregations = metric["allowedAggregations"]!!.jsonArray
                                .map { aggregation -> aggregation.jsonPrimitive.content }
                                .toSet()
                        )
                    },
                    attributes = conventions["attributes"]!!.jsonArray.associate {
                        val attribute = it.jsonObject
                        attribute["name"]!!.jsonPrimitive.content to Attribute(
                            type = attribute["type"]!!.jsonPrimitive.content,
                            values = attribute["values"]?.jsonArray
                                ?.map { value -> value.jsonPrimitive.content }
                                ?.toSet()
                                ?: emptySet()
                        )
                    },
                    scopes = conventions["scopes"]!!.jsonArray
                        .map { it.jsonObject["name"]!!.jsonPrimitive.content }
                        .toSet(),
                    indexes = develocityIndexes["indexes"]!!.jsonArray.associate {
                        val index = it.jsonObject
                        index["name"]!!.jsonPrimitive.content to Index(
                            producer = index["producer"]!!.jsonPrimitive.content,
                            scope = index["scope"]!!.jsonPrimitive.content,
                            metric = index["metric"]!!.jsonPrimitive.content,
                            aggregation = index["aggregation"]!!.jsonPrimitive.content,
                            aggregationScope = index["aggregationScope"]!!.jsonPrimitive.content
                        )
                    }
                )
            }

            private fun resourceJson(path: String): JsonObject {
                val stream = javaClass.classLoader.getResourceAsStream(path)
                    ?: error("Missing test resource $path")
                return ReportJson.json.parseToJsonElement(stream.bufferedReader().readText()).jsonObject
            }

            private fun required(schema: JsonObject): Set<String> =
                schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()

            private fun properties(schema: JsonObject): Set<String> =
                schema["properties"]!!.jsonObject.keys
        }

        private data class Metric(
            val unit: String,
            val allowedAggregations: Set<String>
        )

        private data class Attribute(
            val type: String,
            val values: Set<String>
        )

        private data class Index(
            val producer: String,
            val scope: String,
            val metric: String,
            val aggregation: String,
            val aggregationScope: String
        )
    }
}
