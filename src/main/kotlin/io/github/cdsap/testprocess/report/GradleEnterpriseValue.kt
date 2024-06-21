package io.github.cdsap.testprocess.report

import com.gradle.scan.plugin.BuildScanExtension


class GradleEnterpriseValue(val buildScanExtension: BuildScanExtension) : BuildScanData {
    override fun value(key: String, value: String) {
        buildScanExtension.value(key, value)
    }
}
