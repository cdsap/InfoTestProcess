package io.github.cdsap.testprocess.report

import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import io.github.cdsap.testprocess.model.PersistedState
import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.parser.JstatParser
import org.gradle.api.provider.Provider

class BuildScanReport : Report {

    fun develocityBuildScanReporting(
        develocityConfiguration: DevelocityConfiguration,
        provider: Provider<PersistedState>
    ) {
        develocityConfiguration.buildScan {

            val develocityValue = DevelocityValue(this)
            buildFinished {
                if (provider.isPresent) {
                    extracted(
                        provider.get().processes,
                        provider.get().jstatResults,
                        provider.get().stats,
                        develocityValue
                    )
                }
            }
        }
    }

    override fun extracted(
        processes: Map<Long, TestProcess>,
        jstatResults: Map<Long, String>,
        stats: Stats,
        buildScanData: BuildScanData
    ) {
        processes.forEach {
            if (!jstatResults.containsKey(it.key)) {
                stats.tasksWithoutProcess++
            }
        }
        if (processes.isNotEmpty()) {
            buildScanData.value("Test-Process", "${processes.count()} processes created")
            buildScanData.value(
                "Test-Process-Stats",
                "processes parsed:${stats.totalProcesses}, jstatCalls:${stats.jstatCalls},tasksWithOutProcesses:${stats.tasksWithoutProcess}"
            )
            processes.values.groupBy { it.task }.entries.map {
                buildScanData.value(
                    "Test-Process-processes-by-task-${it.key}",
                    "${it.value.count()}"
                )
            }
            processes.map {
                if (jstatResults.containsKey(it.key)) {
                    val jstatInfo = JstatParser().process(jstatResults[it.key]!!)
                    buildScanData.value(
                        "Test-Process-process-info-${it.key}",
                        "[executor:${it.value.executor}, task:${it.value.task}, xmx:${it.value.max}, " +
                            "usage:${jstatInfo?.usage}Gb, gcTime:${jstatInfo?.gcTime} secs, " +
                            "gcType:${jstatInfo?.typeGC},uptime:${jstatInfo?.uptime} min ]"
                    )
                }
            }
        }
    }
}
