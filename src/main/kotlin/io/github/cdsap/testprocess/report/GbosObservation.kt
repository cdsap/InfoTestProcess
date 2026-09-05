package io.github.cdsap.testprocess.report

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigDecimal

internal data class GbosProducer(
    val name: String,
    val version: String
)

internal data class GbosMeasurement(
    val name: String,
    val value: Double,
    val unit: String,
    val aggregation: String
)

internal data class GbosDiagnostic(
    val code: String,
    val severity: String,
    val message: String? = null
)

internal sealed interface GbosAttributeValue {
    data class Text(val value: String) : GbosAttributeValue
    data class Integer(val value: Long) : GbosAttributeValue
}

/**
 * Internal GBOS observation model. Output adapters (Develocity, file) serialize
 * this; conversion from [ReportDocument] lives in [GbosObservations].
 */
internal data class GbosObservation(
    val schemaVersion: String,
    val producer: GbosProducer,
    val scope: String,
    val aggregationScope: String,
    val attributes: Map<String, GbosAttributeValue>,
    val measurements: List<GbosMeasurement>,
    val partial: Boolean = false,
    val droppedObservations: Int? = null,
    val diagnostics: List<GbosDiagnostic> = emptyList()
)

internal object GbosObservations {
    const val SCHEMA_VERSION = "1.0.0"
    const val OBSERVATION_CUSTOM_VALUE = "gbos.v1.observation"
    const val ATTR_PROCESS_PID = "process.pid"
    const val ATTR_PROCESS_ROLE = "jvm.process.role"
    const val ATTR_TASK_PATH = "gradle.task.path"
    const val ATTR_TEST_EXECUTOR = "gradle.test.executor"
    const val ATTR_GC_NAME = "jvm.gc.name"

    private const val PRODUCER_NAME = "info-test-process"
    private const val PRODUCER_VERSION = "2.1.0"
    private const val PROCESS_SCOPE = "jvm.process"
    private const val TEST_WORKER_ROLE = "test-worker"

    private val PRODUCER = GbosProducer(name = PRODUCER_NAME, version = PRODUCER_VERSION)

    private val ALLOWLISTED_INDEXES = listOf(
        GbosIndex(
            "gbos.v1.index.info_test_process.jvm.process.cpu.cores.max",
            "jvm.process.cpu.cores",
            "max"
        ),
        GbosIndex(
            "gbos.v1.index.info_test_process.jvm.process.cpu.time.sum",
            "jvm.process.cpu.time",
            "sum"
        ),
        GbosIndex(
            "gbos.v1.index.info_test_process.jvm.process.memory.heap.peak.max",
            "jvm.process.memory.heap.peak",
            "max"
        )
    )

    fun from(
        report: ReportDocument,
        maxEntityObservations: Int = WORKERS_PER_PID_CAP
    ): List<GbosObservation> {
        // Heaviest workers first (Develocity value-budget), then PID for stable ties.
        val workersByImportance = report.workers.sortedWith(
            compareByDescending<WorkerProcessInfo> { it.cpuTimeSec }.thenBy { it.pid }
        )
        val emittedWorkers = workersByImportance.take(maxEntityObservations)
        val droppedObservations = workersByImportance.size - emittedWorkers.size
        val observations = emittedWorkers.mapNotNull { workerObservation(it) }.toMutableList()

        val hasLiveWorkers = report.workers.any { !it.statsSnapshotMissing }
        buildObservation(report.summary, hasLiveWorkers, droppedObservations)?.let { observations += it }
        return observations
    }

    fun scalarIndexes(observations: List<GbosObservation>): List<Pair<String, String>> {
        val buildObservation = observations.singleOrNull { it.aggregationScope == "build" }
            ?: return emptyList()
        return ALLOWLISTED_INDEXES.mapNotNull { index ->
            val value = buildObservation.measurements.singleOrNull {
                it.name == index.metric && it.aggregation == index.aggregation
            }?.value
            value?.let { index.name to formatNumber(it) }
        }
    }

    fun encode(observation: GbosObservation): String =
        ReportJson.json.encodeToString(JsonObject.serializer(), toJsonObject(observation))

    fun toJsonObject(observation: GbosObservation): JsonObject = buildJsonObject {
        put("schemaVersion", observation.schemaVersion)
        put(
            "producer",
            buildJsonObject {
                put("name", observation.producer.name)
                put("version", observation.producer.version)
            }
        )
        put("scope", observation.scope)
        put("aggregationScope", observation.aggregationScope)
        put("attributes", attributesJson(observation.attributes))
        if (observation.measurements.isNotEmpty()) {
            put("measurements", measurementsJson(observation.measurements))
        }
        if (observation.partial) {
            put("partial", true)
        }
        observation.droppedObservations?.let { put("droppedObservations", it) }
        if (observation.diagnostics.isNotEmpty()) {
            put("diagnostics", diagnosticsJson(observation.diagnostics))
        }
    }

    private fun workerObservation(worker: WorkerProcessInfo): GbosObservation? {
        val measurements = buildList {
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
            listOf(GbosDiagnostic(code = "gbos.test_process.stats_snapshot_missing", severity = "warning"))
        } else {
            emptyList()
        }
        return observation(
            aggregationScope = "entity",
            attributes = workerAttributes(worker),
            measurements = measurements,
            diagnostics = diagnostics
        )
    }

    private fun buildObservation(
        summary: TestProcessSummary,
        hasLiveWorkers: Boolean,
        droppedObservations: Int
    ): GbosObservation? {
        val measurements = buildList {
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
            listOf(GbosDiagnostic(code = "gbos.limit.observations_dropped", severity = "warning"))
        } else {
            emptyList()
        }
        return observation(
            aggregationScope = "build",
            attributes = mapOf(ATTR_PROCESS_ROLE to GbosAttributeValue.Text(TEST_WORKER_ROLE)),
            measurements = measurements,
            diagnostics = diagnostics,
            partial = droppedObservations > 0,
            droppedObservations = droppedObservations.takeIf { it > 0 }
        )
    }

    private fun workerAttributes(worker: WorkerProcessInfo): Map<String, GbosAttributeValue> = buildMap {
        put(ATTR_PROCESS_PID, GbosAttributeValue.Integer(worker.pid))
        put(ATTR_PROCESS_ROLE, GbosAttributeValue.Text(TEST_WORKER_ROLE))
        put(ATTR_TASK_PATH, GbosAttributeValue.Text(worker.task))
        put(ATTR_TEST_EXECUTOR, GbosAttributeValue.Text(worker.executor))
        if (!worker.statsSnapshotMissing && worker.gcType != "Unknown") {
            put(ATTR_GC_NAME, GbosAttributeValue.Text(worker.gcType))
        }
    }

    private fun observation(
        aggregationScope: String,
        attributes: Map<String, GbosAttributeValue>,
        measurements: List<GbosMeasurement>,
        diagnostics: List<GbosDiagnostic> = emptyList(),
        partial: Boolean = false,
        droppedObservations: Int? = null
    ): GbosObservation = GbosObservation(
        schemaVersion = SCHEMA_VERSION,
        producer = PRODUCER,
        scope = PROCESS_SCOPE,
        aggregationScope = aggregationScope,
        attributes = attributes,
        measurements = measurements,
        partial = partial,
        droppedObservations = droppedObservations,
        diagnostics = diagnostics
    )

    private fun measurement(name: String, value: Double?, unit: String, aggregation: String): GbosMeasurement? {
        if (value == null) return null
        return GbosMeasurement(name = name, value = value, unit = unit, aggregation = aggregation)
    }

    private fun attributesJson(attributes: Map<String, GbosAttributeValue>): JsonObject = buildJsonObject {
        attributes.forEach { (name, value) ->
            when (value) {
                is GbosAttributeValue.Text -> put(name, value.value)
                is GbosAttributeValue.Integer -> put(name, value.value)
            }
        }
    }

    private fun measurementsJson(measurements: List<GbosMeasurement>): JsonArray = buildJsonArray {
        measurements.forEach { measurement ->
            add(
                buildJsonObject {
                    put("name", measurement.name)
                    put("value", jsonNumber(measurement.value))
                    put("unit", measurement.unit)
                    put("aggregation", measurement.aggregation)
                }
            )
        }
    }

    private fun diagnosticsJson(diagnostics: List<GbosDiagnostic>): JsonArray = buildJsonArray {
        diagnostics.forEach { diagnostic ->
            add(
                buildJsonObject {
                    put("code", diagnostic.code)
                    put("severity", diagnostic.severity)
                    diagnostic.message?.let { put("message", it) }
                }
            )
        }
    }

    private fun xmxBytes(xmx: String): Double? = Aggregator.parseXmxGb(xmx)?.let(::gibToBytes)

    private fun gibToBytes(value: Double): Double = Math.round(value * 1024.0 * 1024.0 * 1024.0).toDouble()

    private fun mibToBytes(value: Double): Double = Math.round(value * 1024.0 * 1024.0).toDouble()

    private fun formatNumber(value: Double): String = jsonNumber(value).content

    private fun jsonNumber(value: Double): JsonPrimitive =
        JsonUnquotedLiteral(BigDecimal.valueOf(value).stripTrailingZeros().toPlainString())

    private data class GbosIndex(
        val name: String,
        val metric: String,
        val aggregation: String
    )
}
