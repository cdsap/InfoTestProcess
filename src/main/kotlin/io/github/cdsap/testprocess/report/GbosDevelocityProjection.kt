package io.github.cdsap.testprocess.report

internal object GbosDevelocityProjection {
    fun publish(report: ReportDocument, buildScanData: BuildScanData) {
        val observations = GbosObservations.from(report)
        observations.forEach { observation ->
            buildScanData.value(
                GbosObservations.OBSERVATION_CUSTOM_VALUE,
                GbosObservations.encode(observation)
            )
        }
        GbosObservations.scalarIndexes(observations).forEach { (name, value) ->
            buildScanData.value(name, value)
        }
    }
}
