package io.github.cdsap.testprocess

import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import com.gradle.develocity.agent.gradle.scan.BuildScanConfiguration
import io.github.cdsap.testprocess.model.PersistedState
import io.github.cdsap.testprocess.report.BuildScanData
import io.github.cdsap.testprocess.report.BuildScanReport
import io.github.cdsap.testprocess.report.ReportDocument
import org.gradle.api.provider.Provider

/**
 * Infrastructure adapter that registers Develocity build-finished reporting and
 * translates Develocity [BuildScanConfiguration] callbacks into [BuildScanData].
 */
class DevelocityBuildScanAdapter(
    private val publishGbos: Boolean = false
) {

    fun register(
        develocityConfiguration: DevelocityConfiguration,
        provider: Provider<PersistedState>
    ) {
        develocityConfiguration.buildScan {
            val develocityValue = DevelocityValue(this)
            buildFinished {
                if (provider.isPresent) {
                    val state = provider.get()
                    BuildScanReport(publishGbos).extracted(
                        ReportDocument.from(state.processes, state.runtimeStats, state.stats),
                        develocityValue
                    )
                }
            }
        }
    }
}

internal class DevelocityValue(
    private val buildScanConfiguration: BuildScanConfiguration
) : BuildScanData {
    override fun value(key: String, value: String) {
        buildScanConfiguration.value(key, value)
    }

    override fun tag(name: String) {
        buildScanConfiguration.tag(name)
    }
}
