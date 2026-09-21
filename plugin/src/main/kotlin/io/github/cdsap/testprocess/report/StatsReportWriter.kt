package io.github.cdsap.testprocess.report

import io.github.cdsap.testprocess.model.PersistedState
import kotlinx.serialization.json.Json
import java.io.File

data class StatsReportOutputOptions(
    val develocity: Boolean,
    val persistedTxt: File,
    val persistedJson: File,
    val gbosJson: File,
    val gbosNdjson: File,
    val writeGbosJson: Boolean,
    val writeGbosNdjson: Boolean
)

class StatsReportWriter {

    fun write(
        report: ReportDocument,
        legacyState: PersistedState,
        options: StatsReportOutputOptions
    ) {
        if (options.develocity) {
            val output = options.persistedTxt
            output.parentFile?.mkdirs()
            output.writeText(Json.encodeToString(PersistedState.serializer(), legacyState))
        } else {
            val outputJson = options.persistedJson
            outputJson.parentFile?.mkdirs()
            OutputReport(outputJson).write(report)
        }
        val writeGbosJson = options.writeGbosJson
        val writeGbosNdjson = options.writeGbosNdjson
        if (writeGbosJson || writeGbosNdjson) {
            GbosOutputReport(options.gbosJson, options.gbosNdjson).write(
                report,
                writeJson = writeGbosJson,
                writeNdjson = writeGbosNdjson
            )
        } else {
            options.gbosJson.delete()
            options.gbosNdjson.delete()
        }
    }
}
