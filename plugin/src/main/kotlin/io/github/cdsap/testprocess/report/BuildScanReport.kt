package io.github.cdsap.testprocess.report

class BuildScanReport(
    private val publishGbos: Boolean = false
) {

    fun extracted(
        report: ReportDocument,
        buildScanData: BuildScanData
    ) {
        if (report.workers.isEmpty()) return

        val workers = report.workers
        val summary = report.summary
        val tags = report.tags

        if (publishGbos) {
            GbosDevelocityProjection.publish(report, buildScanData)
        } else {
            // Legacy flat metrics and per-worker values remain the default for
            // builds that have not opted into the GBOS Develocity projection.
            buildScanData.value("testProcess.workers.count", summary.workers.count.toString())
            buildScanData.value("testProcess.workers.tasksWith", summary.workers.tasksWith.toString())
            buildScanData.value("testProcess.workers.snapshotsMissing", summary.workers.snapshotsMissing.toString())
            buildScanData.value("testProcess.cpuCoresAvg.max", summary.cpuCoresAvgMax.toString())
            buildScanData.value("testProcess.cpuTimeSec.sum", summary.cpuTimeSecSum.toString())
            buildScanData.value("testProcess.heapPeakGb.max", summary.heapPeakGbMax.toString())
            buildScanData.value("testProcess.metaspacePeakMb.max", summary.metaspacePeakMbMax.toString())
            buildScanData.value("testProcess.jitSec.sum", summary.jitSecSum.toString())
            buildScanData.value("testProcess.jitSec.max", summary.jitSecMax.toString())
            buildScanData.value("testProcess.classesLoaded.max", summary.classesLoadedMax.toString())
            buildScanData.value("testProcess.gcCollections.sum", summary.gcCollectionsSum.toString())
            buildScanData.value("testProcess.uptimeMin.sum", summary.uptimeMinSum.toString())

            // Per-worker detail — one custom value per PID. Per-task aggregates are
            // derivable from these JSON values, so they are not emitted separately.
            // Preserve the established cap, keeping the heaviest workers first.
            val emitted = workers.sortedByDescending { it.cpuTimeSec }.take(WORKERS_PER_PID_CAP)
            emitted.forEach { worker ->
                buildScanData.value(
                    "testProcess.worker.${worker.pid}",
                    ReportJson.json.encodeToString(WorkerProcessInfo.serializer(), worker)
                )
            }
            buildScanData.value("testProcess.worker.shown", emitted.size.toString())
            if (emitted.size < workers.size) {
                buildScanData.value("testProcess.worker.truncated", "true")
                buildScanData.value("testProcess.worker.total", workers.size.toString())
            }
        }

        // Scan tags for fast categorical filtering in DRV.
        tags.forEach { buildScanData.tag(it) }

    }
}
