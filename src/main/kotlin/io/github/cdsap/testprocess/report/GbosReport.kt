package io.github.cdsap.testprocess.report

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

@Serializable
internal data class GbosReportDocument(
    val schemaVersion: String = GbosReport.SCHEMA_VERSION,
    val resource: JsonObject = buildJsonObject {
        put("build.tool.name", "gradle")
    },
    val observations: List<JsonObject>
)

/**
 * File/NDJSON adapter for GBOS. Observation conversion and JSON shape live in
 * [GbosObservations]; this object owns only the report envelope and sink writing.
 */
internal object GbosReport {
    const val SCHEMA_VERSION = GbosObservations.SCHEMA_VERSION

    fun from(report: ReportDocument): GbosReportDocument? {
        val observations = observations(report)
        if (observations.isEmpty()) return null
        return GbosReportDocument(observations = observations)
    }

    fun observations(report: ReportDocument): List<JsonObject> =
        GbosObservations.from(report).map(GbosObservations::toJsonObject)

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
            // Clear stale opt-in files without paying conversion cost on the default path.
            outputJson.delete()
            outputNdjson.delete()
            return
        }
        val gbosReport = GbosReport.from(report)
        writeSink(outputJson, writeJson, gbosReport) { GbosReport.encodeReport(it) }
        writeSink(outputNdjson, writeNdjson, gbosReport) { document ->
            document.observations.joinToString(separator = "\n", postfix = "\n") {
                GbosReport.encodeObservation(it)
            }
        }
    }

    private fun writeSink(
        output: File,
        enabled: Boolean,
        gbosReport: GbosReportDocument?,
        encode: (GbosReportDocument) -> String
    ) {
        if (!enabled || gbosReport == null) {
            output.delete()
            return
        }
        output.parentFile?.mkdirs()
        output.writeText(encode(gbosReport))
    }
}
