package io.github.cdsap.testprocess.report

import com.gradle.develocity.agent.gradle.scan.BuildScanConfiguration

class DevelocityValue(val develocityConfiguration: BuildScanConfiguration) : BuildScanData {
    override fun value(key: String, value: String) {
        develocityConfiguration.value(key, value)
    }
}
