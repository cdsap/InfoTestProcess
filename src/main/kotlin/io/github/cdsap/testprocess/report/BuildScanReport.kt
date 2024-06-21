package io.github.cdsap.testprocess.report

import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import com.gradle.scan.plugin.BuildScanExtension
import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.parser.JstatParser

class BuildScanReport {
    fun gradleEnterpriseBuildScanReporting(
        buildScanExtension: BuildScanExtension,
        processes: MutableMap<Long, TestProcess>,
        jstatResults: MutableMap<Long, String>,
        stats: Stats
    ) {
        buildScanExtension.buildFinished {
            val gradleEnterpriseValue = GradleEnterpriseValue(buildScanExtension)
            extracted(processes, jstatResults, stats, gradleEnterpriseValue)
        }
    }


    fun develocityBuildScanReporting(
        develocityConfiguration: DevelocityConfiguration,
        processes: MutableMap<Long, TestProcess>,
        jstatResults: MutableMap<Long, String>,
        stats: Stats
    ) {
        develocityConfiguration.buildScan {
            val develocityValue = DevelocityValue(this)
            buildFinished {
                extracted(processes, jstatResults, stats, develocityValue)
            }
        }

    }

    private fun extracted(
        processes: MutableMap<Long, TestProcess>,
        jstatResults: MutableMap<Long, String>,
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
