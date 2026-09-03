package io.github.cdsap.testprocess.report

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.math.BigDecimal

internal object GbosDevelocityProjection {
    private const val OBSERVATION_CUSTOM_VALUE = "gbos.v1.observation"
    private const val SCHEMA_VERSION = "1.0.0"
    private const val PRODUCER_NAME = "info-test-process"
    private const val PRODUCER_VERSION = "2.1.0"
    private const val PROCESS_SCOPE = "jvm.process"
    private const val ROLE_ATTRIBUTE = "jvm.process.role"
    private const val TEST_WORKER_ROLE = "test-worker"

    private val ALLOWLISTED_INDEXES = listOf(
        Triple(
            "gbos.v1.index.info_test_process.jvm.process.cpu.cores.max",
            "jvm.process.cpu.cores",
            "max"
        ),
        Triple(
            "gbos.v1.index.info_test_process.jvm.process.cpu.time.sum",
            "jvm.process.cpu.time",
            "sum"
        ),
        Triple(
            "gbos.v1.index.info_test_process.jvm.process.memory.heap.peak.max",
            "jvm.process.memory.heap.peak",
            "max"
        )
    )

    fun publish(report: ReportDocument, buildScanData: BuildScanData) {
        val workersByImportance = report.workers.sortedByDescending { it.cpuTimeSec }
        val emittedWorkers = workersByImportance.take(WORKERS_PER_PID_CAP)
        val droppedObservations = workersByImportance.size - emittedWorkers.size

        emittedWorkers
            .mapNotNull { workerObservation(it) }
            .forEach { observation ->
                buildScanData.value(OBSERVATION_CUSTOM_VALUE, encode(observation))
            }

        val hasLiveWorkers = report.workers.any { !it.statsSnapshotMissing }
        val buildObservation = buildObservation(report.summary, hasLiveWorkers, droppedObservations) ?: return
        buildScanData.value(OBSERVATION_CUSTOM_VALUE, encode(buildObservation))
        scalarIndexes(buildObservation).forEach { (name, value) ->
            buildScanData.value(name, formatNumber(value))
        }
    }

    private fun workerObservation(worker: WorkerProcessInfo): JsonObject? {
        val measurements = buildJsonArray {
            measurement("jvm.process.memory.heap.limit", xmxBytes(worker.xmx), "By", "last")?.let { add(it) }
            if (!worker.statsSnapshotMissing) {
                measurement("jvm.process.uptime", worker.uptimeMin * 60.0, "s", "last")?.let { add(it) }
                measurement("jvm.process.cpu.time", worker.cpuTimeSec, "s", "sum")?.let { add(it) }
                measurement("jvm.process.cpu.cores", worker.cpuCoresAvg, "{core}", "last")?.let { add(it) }
                measurement("jvm.process.memory.heap.used", gibToBytes(worker.heapUsageGb), "By", "last")?.let { add(it) }
                measurement("jvm.process.memory.heap.peak", gibToBytes(worker.heapPeakGb), "By", "max")?.let { add(it) }
                measurement("jvm.process.memory.metaspace.peak", mibToBytes(worker.metaspacePeakMb), "By", "max")?.let { add(it) }
                measurement("jvm.process.gc.collections", worker.gcCollections.toDouble(), "{collection}", "count")?.let { add(it) }
                measurement("jvm.process.gc.time", worker.gcTimeSec, "s", "sum")?.let { add(it) }
                measurement("jvm.process.jit.time", worker.jitSec, "s", "sum")?.let { add(it) }
                measurement("jvm.process.classes.loaded", worker.classesLoaded.toDouble(), "{class}", "last")?.let { add(it) }
                measurement("jvm.process.threads.peak", worker.peakThreads.toDouble(), "{thread}", "max")?.let { add(it) }
            }
        }
        if (measurements.isEmpty()) return null

        val diagnostics = if (worker.statsSnapshotMissing) {
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("code", "gbos.test_process.stats_snapshot_missing")
                        put("severity", "warning")
                    }
                )
            }
        } else {
            null
        }
        return observation("entity", workerAttributes(worker), measurements, diagnostics)
    }

    private fun buildObservation(
        summary: TestProcessSummary,
        hasLiveWorkers: Boolean,
        droppedObservations: Int
    ): JsonObject? {
        val measurements = buildJsonArray {
            if (hasLiveWorkers) {
                measurement("jvm.process.cpu.cores", summary.cpuCoresAvgMax, "{core}", "max")?.let { add(it) }
                measurement("jvm.process.cpu.time", summary.cpuTimeSecSum, "s", "sum")?.let { add(it) }
                measurement("jvm.process.memory.heap.peak", gibToBytes(summary.heapPeakGbMax), "By", "max")?.let { add(it) }
                measurement("jvm.process.memory.metaspace.peak", mibToBytes(summary.metaspacePeakMbMax), "By", "max")?.let { add(it) }
                measurement("jvm.process.jit.time", summary.jitSecSum, "s", "sum")?.let { add(it) }
                measurement("jvm.process.jit.time", summary.jitSecMax, "s", "max")?.let { add(it) }
                measurement("jvm.process.classes.loaded", summary.classesLoadedMax.toDouble(), "{class}", "max")?.let { add(it) }
                measurement("jvm.process.gc.collections", summary.gcCollectionsSum.toDouble(), "{collection}", "sum")?.let { add(it) }
                measurement("jvm.process.uptime", summary.uptimeMinSum * 60.0, "s", "sum")?.let { add(it) }
            }
        }
        if (measurements.isEmpty()) return null

        val diagnostics = if (droppedObservations > 0) {
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("code", "gbos.limit.observations_dropped")
                        put("severity", "warning")
                    }
                )
            }
        } else {
            null
        }
        return observation(
            aggregationScope = "build",
            attributes = buildJsonObject { put(ROLE_ATTRIBUTE, TEST_WORKER_ROLE) },
            measurements = measurements,
            diagnostics = diagnostics,
            partial = droppedObservations > 0,
            droppedObservations = droppedObservations.takeIf { it > 0 }
        )
    }

    private fun workerAttributes(worker: WorkerProcessInfo): JsonObject = buildJsonObject {
        put("process.pid", worker.pid)
        put(ROLE_ATTRIBUTE, TEST_WORKER_ROLE)
        put("gradle.task.path", worker.task)
        put("gradle.test.executor", worker.executor)
        if (!worker.statsSnapshotMissing && worker.gcType != "Unknown") {
            put("jvm.gc.name", worker.gcType)
        }
    }

    private fun observation(
        aggregationScope: String,
        attributes: JsonObject,
        measurements: JsonArray,
        diagnostics: JsonArray? = null,
        partial: Boolean = false,
        droppedObservations: Int? = null
    ): JsonObject = buildJsonObject {
        put("schemaVersion", SCHEMA_VERSION)
        put(
            "producer",
            buildJsonObject {
                put("name", PRODUCER_NAME)
                put("version", PRODUCER_VERSION)
            }
        )
        put("scope", PROCESS_SCOPE)
        put("aggregationScope", aggregationScope)
        put("attributes", attributes)
        if (measurements.isNotEmpty()) {
            put("measurements", measurements)
        }
        if (partial) {
            put("partial", true)
        }
        if (droppedObservations != null) {
            put("droppedObservations", droppedObservations)
        }
        if (diagnostics != null) {
            put("diagnostics", diagnostics)
        }
    }

    private fun scalarIndexes(buildObservation: JsonObject): List<Pair<String, Double>> {
        val measurements = buildObservation["measurements"] as? JsonArray ?: return emptyList()
        return ALLOWLISTED_INDEXES.mapNotNull { (indexName, metric, aggregation) ->
            val value = measurements
                .map { it as JsonObject }
                .singleOrNull {
                    it["name"] == JsonPrimitive(metric) &&
                        it["aggregation"] == JsonPrimitive(aggregation)
                }
                ?.get("value")
                ?.jsonPrimitive
                ?.double
            value?.let { indexName to it }
        }
    }

    private fun measurement(name: String, value: Double?, unit: String, aggregation: String): JsonObject? {
        if (value == null) return null
        return buildJsonObject {
            put("name", name)
            put("value", jsonNumber(value))
            put("unit", unit)
            put("aggregation", aggregation)
        }
    }

    private fun xmxBytes(xmx: String): Double? = Aggregator.parseXmxGb(xmx)?.let(::gibToBytes)

    private fun gibToBytes(value: Double): Double = Math.round(value * 1024.0 * 1024.0 * 1024.0).toDouble()

    private fun mibToBytes(value: Double): Double = Math.round(value * 1024.0 * 1024.0).toDouble()

    private fun formatNumber(value: Double): String = jsonNumber(value).content

    private fun jsonNumber(value: Double): JsonPrimitive =
        JsonUnquotedLiteral(BigDecimal.valueOf(value).stripTrailingZeros().toPlainString())

    private fun encode(observation: JsonObject): String =
        ReportJson.json.encodeToString(JsonObject.serializer(), observation)
}
