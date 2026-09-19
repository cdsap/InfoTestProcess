package io.github.cdsap.testprocess.report

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.model.WorkerRuntimeStats
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishedGbosSchemaValidationTest {
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

    private val mapper = ObjectMapper()
    private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)

    @Test
    fun generatedReportAndProjectionValidateAgainstPublishedArtifact() {
        val report = ReportDocument.from(processes, runtimeStats, Stats())!!
        val reportJson = GbosReport.encodeReport(GbosReport.from(report)!!)

        assertValid("schema/report.schema.json", reportJson)

        val scanData = RecordingBuildScanData()
        BuildScanReport(publishGbos = true).extracted(report, scanData)
        val projection = buildJsonObject {
            put("schemaVersion", "1.0.0")
            put(
                "customValues",
                buildJsonArray {
                    scanData.values
                        .filter { it.first.startsWith("gbos.v1.") }
                        .forEach { (name, value) ->
                            add(buildJsonObject {
                                put("name", name)
                                put("value", value)
                            })
                        }
                }
            )
            put("tags", buildJsonArray { })
        }
        assertValid("schema/develocity-projection.schema.json", projection.toString())
    }

    @Test
    fun invalidPublishedContractDocumentIsRejected() {
        val invalidReport = """
            {"schemaVersion":"1.0.0","resource":{"build.tool.name":"gradle"},"observations":[]}
        """.trimIndent()

        val errors = schema("schema/report.schema.json").validate(mapper.readTree(invalidReport))
        assertTrue("expected the invalid report to be rejected", errors.isNotEmpty())
    }

    private fun assertValid(schemaPath: String, json: String) {
        val errors = schema(schemaPath).validate(mapper.readTree(json))
        assertTrue("$schemaPath rejected generated GBOS: $errors", errors.isEmpty())
    }

    private fun schema(path: String) =
        schemaFactory.getSchema(requireNotNull(javaClass.getResource("/$path")).toURI())

    private class RecordingBuildScanData : BuildScanData {
        val values = mutableListOf<Pair<String, String>>()

        override fun value(key: String, value: String) {
            values += key to value
        }

        override fun tag(name: String) = Unit
    }
}
