package io.github.cdsap.testprocess.report

import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess

interface Report {
    fun extracted(
        processes: Map<Long, TestProcess>,
        jstatResults: Map<Long, String>,
        stats: Stats,
        buildScanData: BuildScanData
    )

    fun operation() {

    }
}
