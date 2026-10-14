package io.github.cdsap.testprocess.report

import io.github.cdsap.testprocess.model.PersistedState
import kotlinx.serialization.json.Json
import java.io.File

data class StatsOutputOptions(
    val develocity: Boolean,
    val persistedTxt: File,
    val persistedJson: File,
    val gbosJson: File,
    val gbosNdjson: File,
    val writeGbosJson: Boolean,
    val writeGbosNdjson: Boolean
)

class StatsOutputWriter {

    fun write(
        report: ReportDocument,
        legacyState: PersistedState,
        options: StatsOutputOptions
    ) {
        // Render every enabled sink before replacing any stable path. A failed
        // generation therefore leaves the complete previous report set intact.
        val legacyContent = if (options.develocity) {
            Json.encodeToString(PersistedState.serializer(), legacyState)
        } else {
            OutputReport(options.persistedJson).render(report)
        }
        val gbosContent = GbosOutputReport(options.gbosJson, options.gbosNdjson).render(
            report,
            writeJson = options.writeGbosJson,
            writeNdjson = options.writeGbosNdjson
        )

        if (options.develocity) {
            AtomicFileWriter.replace(options.persistedTxt, legacyContent)
        } else {
            AtomicFileWriter.replace(options.persistedJson, legacyContent)
        }
        gbosContent.first?.let { AtomicFileWriter.replace(options.gbosJson, it) }
            ?: options.gbosJson.delete()
        gbosContent.second?.let { AtomicFileWriter.replace(options.gbosNdjson, it) }
            ?: options.gbosNdjson.delete()
    }
}
