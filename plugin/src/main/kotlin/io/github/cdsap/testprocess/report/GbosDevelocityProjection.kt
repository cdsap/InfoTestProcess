package io.github.cdsap.testprocess.report

internal object GbosDevelocityProjection {
    fun publish(report: ReportDocument, buildScanData: BuildScanData) {
        val observations = GbosObservations.from(report)
        if (observations.isNotEmpty()) {
            buildScanData.value(GbosObservations.SCHEMA_CUSTOM_VALUE, GbosObservations.SCHEMA_VERSION)
            buildScanData.value(GbosObservations.VERSION_CUSTOM_VALUE, GbosObservations.CONTRACT_VERSION)
            buildScanData.value(GbosObservations.PRODUCER_CUSTOM_VALUE, GbosObservations.PRODUCER_NAME)
        }
        observations.forEach { observation ->
            buildScanData.value(
                GbosObservations.OBSERVATION_CUSTOM_VALUE,
                GbosObservations.encodeFragment(observation)
            )
        }
        GbosObservations.scalarIndexes(observations).forEach { (name, value) ->
            buildScanData.value(name, value)
        }
    }
}
