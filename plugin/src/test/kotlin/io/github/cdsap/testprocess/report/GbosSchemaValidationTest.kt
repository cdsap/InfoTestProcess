package io.github.cdsap.testprocess.report

import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.model.WorkerRuntimeStats
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test
import java.io.File

class GbosSchemaValidationTest {
    private val processes = mapOf(
        13402L to TestProcess(task = ":core:test", executor = "Gradle Test Executor 5", max = "512m")
    )
    private val runtimeStats = mapOf(
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
    )

    @Test
    fun publishedContractResourcesResolveFromMavenArtifactClasspath() {
        val loader = javaClass.classLoader
        assert(loader.getResource("gbos/schema/report.schema.json") == null) {
            "schema files must not be copied into src/test/resources"
        }
        listOf(
            "schema/report.schema.json",
            "schema/observation.schema.json",
            "schema/observation-batch.schema.json",
            "schema/develocity-projection.schema.json",
            "registry/semantic-conventions.json",
            "registry/develocity-indexes.json"
        ).forEach { path ->
            assert(loader.getResource(path) != null) {
                "expected $path on the test classpath from build-observability-schema"
            }
        }
        // Loading initializes schemas and validates registry documents (including relative $ref).
        GbosContract.load()
    }

    @Test
    fun generatedJsonNdjsonAndDevelocityExamplesValidateAgainstPublicContract() {
        val scanData = RecordingBuildScanData()
        BuildScanReport(publishGbos = true).extracted(
            ReportDocument.from(processes, runtimeStats, Stats()),
            scanData
        )

        val gbosValues = scanData.values.filter { it.first.startsWith("gbos.v1.") }
        assert(gbosValues.isNotEmpty()) { "expected generated GBOS Develocity values" }
        val schema = GbosContract.load()

        val batches = gbosValues
            .filter { it.first == "gbos.v1.observations" }
            .map { ReportJson.json.parseToJsonElement(it.second).jsonObject }
        assert(batches.size == 1)
        val batch = batches.single()
        schema.validateBatch(batch)
        assert(batch["schemaVersion"]!!.jsonPrimitive.content == "1.0.0")
        assert(batch["producer"]!!.jsonObject["name"]!!.jsonPrimitive.content == "info-test-process")
        assert(batch["observations"]!!.jsonArray.all { observation ->
            observation.jsonObject.keys.none { it == "schemaVersion" || it == "producer" }
        })

        val observationJson = gbosValues
            .filter { it.first == "gbos.v1.observation" }
            .map { it.second }
        assert(observationJson.size == 2)

        val observations = observationJson.map {
            ReportJson.json.parseToJsonElement(it).jsonObject
        }
        observations.forEachIndexed { index, observation ->
            schema.validateObservation(observation, "observation[$index]")
        }

        val reportDocument = buildJsonObject {
            put("schemaVersion", "1.0.0")
            put(
                "resource",
                buildJsonObject {
                    put("build.tool.name", "gradle")
                }
            )
            put("observations", JsonArray(observations))
        }
        schema.validateReport(reportDocument)

        val producer = observations.first()["producer"]!!.jsonObject
        val batchDocument = buildJsonObject {
            put("schemaVersion", "1.0.0")
            put("producer", producer)
            put(
                "observations",
                buildJsonArray {
                    observations.forEach { observation ->
                        add(
                            buildJsonObject {
                                put("scope", observation["scope"]!!.jsonPrimitive.content)
                                put(
                                    "aggregationScope",
                                    observation["aggregationScope"]!!.jsonPrimitive.content
                                )
                                put("attributes", observation["attributes"]!!)
                                observation["measurements"]?.let { put("measurements", it) }
                                observation["diagnostics"]?.let { put("diagnostics", it) }
                            }
                        )
                    }
                }
            )
        }
        schema.validateBatch(batchDocument)

        val ndjson = observationJson.joinToString("\n")
        val ndjsonFile = File.createTempFile("gbos-schema", ".ndjson")
        try {
            ndjsonFile.writeText(ndjson)
            val lines = ndjsonFile.readLines()
            assert(lines.size == 2)
            lines.forEachIndexed { index, line ->
                assert("\n" !in line)
                schema.validateObservation(
                    ReportJson.json.parseToJsonElement(line).jsonObject,
                    "ndjson[$index]"
                )
            }
        } finally {
            ndjsonFile.delete()
        }

        gbosValues
            .filter { it.first.startsWith("gbos.v1.index.") }
            .forEach { (name, value) -> schema.validateIndex(name, value) }

        val projection = buildJsonObject {
            put("schemaVersion", "1.0.0")
            put(
                "customValues",
                buildJsonArray {
                    gbosValues.forEach { (name, value) ->
                        add(
                            buildJsonObject {
                                put("name", name)
                                put("value", value)
                            }
                        )
                    }
                }
            )
            put("tags", buildJsonArray { })
        }
        schema.validateDevelocityProjection(projection)

        // Legacy keys remain available alongside the opt-in projection.
        assert(scanData.values.any { it.first == "testProcess.cpuTimeSec.sum" })
        assert(scanData.values.any { it.first == "testProcess.worker.13402" })
        assert(scanData.values.none { it.first.startsWith("gbos.v1.") && "13402" in it.first })
    }

    @Test
    fun deliberatelyInvalidDocumentIsRejectedByPublishedSchema() {
        val schema = GbosContract.load()
        val invalid = buildJsonObject {
            put("schemaVersion", "9.9.9")
            put("resource", buildJsonObject { put("build.tool.name", "gradle") })
            put("observations", buildJsonArray { })
        }
        var rejected = false
        try {
            schema.validateReport(invalid)
        } catch (error: AssertionError) {
            rejected = true
            assert("failed published schema validation" in error.message.orEmpty())
        }
        assert(rejected) { "expected invalid report to fail schema validation" }
    }

    @Test
    fun disabledByDefaultProducesNoGbosArtifactsToValidate() {
        val scanData = RecordingBuildScanData()
        BuildScanReport().extracted(
            ReportDocument.from(processes, runtimeStats, Stats()),
            scanData
        )

        assert(scanData.values.none { it.first.startsWith("gbos.v1.") })
        assert(scanData.values.any { it.first.startsWith("testProcess.") })
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
