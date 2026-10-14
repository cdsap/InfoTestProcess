package io.github.cdsap.testprocess.report

import java.io.File

internal class GbosOutputReport(
    private val outputJson: File,
    private val outputNdjson: File
) {
    fun write(report: ReportDocument, writeJson: Boolean, writeNdjson: Boolean) {
        val (json, ndjson) = render(report, writeJson, writeNdjson)
        replaceOrDelete(outputJson, json)
        replaceOrDelete(outputNdjson, ndjson)
    }

    fun render(
        report: ReportDocument,
        writeJson: Boolean,
        writeNdjson: Boolean
    ): Pair<String?, String?> {
        if (!writeJson && !writeNdjson) {
            return null to null
        }
        val observations = GbosObservations.from(report)
        if (observations.isEmpty()) return null to null
        return (
            if (writeJson) GbosObservations.encodeReport(observations) else null
            ) to (
            if (writeNdjson) observations.joinToString(
                separator = "\n",
                postfix = "\n",
                transform = GbosObservations::encode
            ) else null
            )
    }

    private fun replaceOrDelete(output: File, content: String?) {
        if (content == null) output.delete() else AtomicFileWriter.replace(output, content)
    }
}
