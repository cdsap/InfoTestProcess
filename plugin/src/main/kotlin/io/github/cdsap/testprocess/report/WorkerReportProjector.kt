package io.github.cdsap.testprocess.report

import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.model.WorkerRuntimeStats

/** Projects collected worker state into the report-domain worker view. */
internal object WorkerReportProjector {
    fun from(
        processes: Map<Long, TestProcess>,
        runtimeStats: Map<Long, WorkerRuntimeStats>
    ): List<WorkerProcessInfo> =
        processes.map { (pid, proc) -> project(proc, runtimeStats[pid], pid) }

    private fun project(proc: TestProcess, stats: WorkerRuntimeStats?, pid: Long): WorkerProcessInfo {
        if (stats == null) {
            return WorkerProcessInfo(
                pid = pid,
                task = proc.task,
                executor = proc.executor,
                xmx = proc.max,
                uptimeMin = 0.0,
                cpuTimeSec = 0.0,
                cpuCoresAvg = 0.0,
                heapUsageGb = 0.0,
                heapPeakGb = 0.0,
                metaspacePeakMb = 0.0,
                gcType = "Unknown",
                gcCollections = 0,
                gcTimeSec = 0.0,
                jitSec = 0.0,
                classesLoaded = -1,
                peakThreads = -1,
                statsSnapshotMissing = true
            )
        }
        return WorkerProcessInfo(
            pid = pid,
            task = proc.task,
            executor = proc.executor,
            xmx = proc.max,
            uptimeMin = StatFormat.minutes(stats.uptimeMs),
            cpuTimeSec = StatFormat.seconds(stats.cpuTimeMs),
            cpuCoresAvg = StatFormat.cores(stats.cpuTimeMs, stats.uptimeMs),
            heapUsageGb = StatFormat.gigs(stats.usedHeapBytes),
            heapPeakGb = StatFormat.gigs(stats.peakHeapBytes),
            metaspacePeakMb = StatFormat.megs(stats.peakMetaspaceBytes),
            gcType = stats.gcType,
            gcCollections = stats.gcCollections,
            gcTimeSec = StatFormat.seconds(stats.gcTimeMs),
            jitSec = StatFormat.seconds(stats.jitTimeMs),
            classesLoaded = stats.classesLoaded,
            peakThreads = stats.peakThreads
        )
    }
}
