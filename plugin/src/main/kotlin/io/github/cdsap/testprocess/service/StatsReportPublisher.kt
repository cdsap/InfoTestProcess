package io.github.cdsap.testprocess.service

import io.github.cdsap.testprocess.agent.WorkerRegistry
import io.github.cdsap.testprocess.agent.WorkerStateCollector
import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.report.ReportDocument
import io.github.cdsap.testprocess.report.StatsOutputOptions
import io.github.cdsap.testprocess.report.StatsOutputWriter
import java.io.File

/**
 * Application-level collaborator that collects worker state and publishes the
 * end-of-build report. [StatsBuildService] remains the Gradle lifecycle adapter.
 */
class StatsReportPublisher(
    private val writer: StatsOutputWriter
) {
    constructor() : this(StatsOutputWriter())

    fun publish(
        registryDir: File,
        processes: Map<Long, TestProcess>,
        stats: Stats,
        options: StatsOutputOptions
    ) {
        val payload = WorkerStateCollector.collect(
            WorkerRegistry.read(registryDir),
            WorkerRegistry.readStats(registryDir),
            processes,
            stats
        )
        val report = ReportDocument.from(payload.processes, payload.runtimeStats, payload.stats)
        writer.write(report, payload, options)
    }
}
