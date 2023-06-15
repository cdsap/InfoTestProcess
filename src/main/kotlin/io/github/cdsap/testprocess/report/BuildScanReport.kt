package io.github.cdsap.testprocess.report

import com.gradle.scan.plugin.BuildScanExtension
import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.parser.JstatParser

class BuildScanReport {
    fun buildScanReporting(
        buildScanExtension: BuildScanExtension,
        processes: MutableMap<Long, TestProcess>,
        jstatResults: MutableMap<Long, String>,
        stats: Stats
    ) {
        buildScanExtension.buildFinished {

            processes.forEach {
                if (!jstatResults.containsKey(it.key)) {
                    stats.tasksWithoutProcess++
                }
            }
            if (processes.isNotEmpty()) {
                buildScanExtension.value("Test-Process", "${processes.count()} processes created")
                buildScanExtension.value(
                    "Test-Process-Stats",
                    "processes parsed:${stats.totalProcesses}, jstatCalls:${stats.jstatCalls},tasksWithOutProcesses:${stats.tasksWithoutProcess}"
                )
                processes.values.groupBy { it.task }.entries.map {
                    buildScanExtension.value(
                        "Test-Process-processes-by-task-${it.key}",
                        "${it.value.count()}"
                    )
                }
                processes.map {
                    if (jstatResults.containsKey(it.key)) {
                        val jstatInfo = JstatParser().process(jstatResults[it.key]!!)
                        buildScanExtension.value(
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
}
