package io.github.cdsap.testprocess.report

import io.github.cdsap.testprocess.model.PersistedState
import kotlinx.serialization.json.Json
import java.io.File

internal class LegacyOutputWriter {
    fun prepare(
        report: ReportDocument,
        legacyState: PersistedState,
        develocity: Boolean,
        persistedTxt: File,
        persistedJson: File
    ): LegacyOutput {
        return if (develocity) {
            LegacyOutput(
                persistedTxt,
                Json.encodeToString(PersistedState.serializer(), legacyState)
            )
        } else {
            LegacyOutput(persistedJson, OutputReport(persistedJson).render(report))
        }
    }

    fun write(
        report: ReportDocument,
        legacyState: PersistedState,
        develocity: Boolean,
        persistedTxt: File,
        persistedJson: File
    ) {
        write(prepare(report, legacyState, develocity, persistedTxt, persistedJson))
    }

    fun write(output: LegacyOutput) {
        AtomicFileWriter.replace(output.file, output.content)
    }
}

internal data class LegacyOutput(
    val file: File,
    val content: String
)
