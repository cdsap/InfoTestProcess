package io.github.cdsap.testprocess.report

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.math.BigDecimal

@Serializable
internal data class GbosReportDocument(
    val schemaVersion: String = GbosObservations.SCHEMA_VERSION,
    val resource: JsonObject = buildJsonObject { put("build.tool.name", "gradle") },
    val observations: List<JsonObject>
)

internal object GbosReport {
    const val SCHEMA_VERSION = GbosObservations.SCHEMA_VERSION
    const val OBSERVATION_CUSTOM_VALUE = GbosObservations.OBSERVATION_CUSTOM_VALUE

    fun from(report: ReportDocument): GbosReportDocument =
        GbosReportDocument(observations = observations(report))

    fun observations(report: ReportDocument): List<JsonObject> =
        GbosObservations.from(report).map(GbosObservations::toJsonObject)

    fun scalarIndexes(observations: List<JsonObject>): List<Pair<String, String>> =
        listOf(
            "gbos.v1.index.info_test_process.jvm.process.cpu.cores.max" to
                ("jvm.process.cpu.cores" to "max"),
            "gbos.v1.index.info_test_process.jvm.process.cpu.time.sum" to
                ("jvm.process.cpu.time" to "sum"),
            "gbos.v1.index.info_test_process.jvm.process.memory.heap.peak.max" to
                ("jvm.process.memory.heap.peak" to "max")
        ).mapNotNull { (index, metric) ->
            val buildObservation = observations.singleOrNull {
                it["aggregationScope"] == JsonPrimitive("build")
            } ?: return@mapNotNull null
            val measurements = buildObservation["measurements"] as? JsonArray ?: return@mapNotNull null
            val value = measurements.map { it as JsonObject }.singleOrNull {
                it["name"] == JsonPrimitive(metric.first) && it["aggregation"] == JsonPrimitive(metric.second)
            }?.get("value")?.jsonPrimitive?.double ?: return@mapNotNull null
            index to BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        }

    fun encodeObservation(observation: JsonObject): String =
        ReportJson.json.encodeToString(JsonObject.serializer(), observation)

    fun encodeReport(report: GbosReportDocument): String =
        ReportJson.prettyJson.encodeToString(GbosReportDocument.serializer(), report)

}

internal class GbosOutputReport(
    private val outputJson: File,
    private val outputNdjson: File
) {
    fun write(report: ReportDocument, writeJson: Boolean, writeNdjson: Boolean) {
        if (!writeJson && !writeNdjson) {
            outputJson.delete()
            outputNdjson.delete()
            return
        }
        val observations = GbosObservations.from(report)
        writeSink(outputJson, writeJson, observations) { GbosObservations.encodeReport(it) }
        writeSink(outputNdjson, writeNdjson, observations) { entries ->
            entries.joinToString(separator = "\n", postfix = "\n", transform = GbosObservations::encode)
        }
    }

    private fun writeSink(
        output: File,
        enabled: Boolean,
        observations: List<GbosObservation>,
        encode: (List<GbosObservation>) -> String
    ) {
        if (!enabled || observations.isEmpty()) {
            output.delete()
            return
        }
        output.parentFile?.mkdirs()
        output.writeText(encode(observations))
    }
}
