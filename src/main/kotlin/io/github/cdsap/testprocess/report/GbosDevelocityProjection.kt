package io.github.cdsap.testprocess.report

internal object GbosDevelocityProjection {
    fun publish(report: ReportDocument, buildScanData: BuildScanData) {
        val observations = GbosReport.observations(report)
        observations.forEach { observation ->
            buildScanData.value(GbosReport.OBSERVATION_CUSTOM_VALUE, GbosReport.encodeObservation(observation))
        }
        GbosReport.scalarIndexes(observations).forEach { (name, value) ->
            buildScanData.value(name, value)
        }
    }
}
