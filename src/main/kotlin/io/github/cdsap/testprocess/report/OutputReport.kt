package io.github.cdsap.testprocess.report

import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.parser.JstatParser
import kotlinx.serialization.json.Json
import java.io.File

class OutputReport(val outputJson: File) : Report {


    override fun extracted(
        processes: Map<Long, TestProcess>,
        jstatResults: Map<Long, String>,
        stats: Stats,
        buildScanData: BuildScanData
    ) {

        val entries = linkedMapOf<String, String>() // preserves order

        processes.forEach { (pid, _) ->
            if (!jstatResults.containsKey(pid)) {
                stats.tasksWithoutProcess++
            }
        }

        if (processes.isNotEmpty()) {
            entries["Test-Process"] = "${processes.count()} processes created"
            entries["Test-Process-Stats"] =
                "processes parsed:${stats.totalProcesses}, jstatCalls:${stats.jstatCalls},tasksWithOutProcesses:${stats.tasksWithoutProcess}"

            processes.values
                .groupBy { it.task }
                .forEach { (task, list) ->
                    entries["Test-Process-processes-by-task-$task"] = "${list.count()}"
                }

            processes.forEach { (pid, proc) ->
                if (jstatResults.containsKey(pid)) {
                    val jstatInfo = JstatParser().process(jstatResults[pid]!!)
                    entries["Test-Process-process-info-$pid"] =
                        "[executor:${proc.executor}, task:${proc.task}, xmx:${proc.max}, " +
                            "usage:${jstatInfo?.usage}Gb, gcTime:${jstatInfo?.gcTime} secs, " +
                            "gcType:${jstatInfo?.typeGC},uptime:${jstatInfo?.uptime} min ]"
                }
            }
        }

        val json = Json { prettyPrint = true }.encodeToString(entries) // Map -> JSON object
        outputJson.writeText(json)
    }
}
