package io.github.cdsap.testprocess.report

import io.github.cdsap.testprocess.model.PersistedState
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
        val legacyOutputWriter = LegacyOutputWriter()
        val legacyOutput = legacyOutputWriter.prepare(
            report = report,
            legacyState = legacyState,
            develocity = options.develocity,
            persistedTxt = options.persistedTxt,
            persistedJson = options.persistedJson
        )
        val gbosOutputWriter = GbosOutputReport(options.gbosJson, options.gbosNdjson)
        val gbosOutput = gbosOutputWriter.render(
            report,
            writeJson = options.writeGbosJson,
            writeNdjson = options.writeGbosNdjson
        )
        legacyOutputWriter.write(legacyOutput)
        gbosOutputWriter.write(gbosOutput)
    }
}
