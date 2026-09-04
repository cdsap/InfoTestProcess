package io.github.cdsap.testprocess.report

import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.model.WorkerRuntimeStats
import org.junit.Test

class GbosObservationTest {
    private val report = ReportDocument.from(
        processes = mapOf(
            13402L to TestProcess(task = ":core:test", executor = "Gradle Test Executor 5", max = "512m")
        ),
        runtimeStats = mapOf(
            13402L to WorkerRuntimeStats(
                pid = 13402L,
                uptimeMs = 1_200,
                cpuTimeMs = 3_050,
                usedHeapBytes = 100_000_000,
                peakHeapBytes = 150_323_855,
                peakMetaspaceBytes = 15_246_295,
                maxHeapBytes = 536_870_912,
                gcCollections = 2,
                gcTimeMs = 0,
                gcType = "G1",
                jitTimeMs = 2_280,
                classesLoaded = 3_503,
                peakThreads = 10
            )
        ),
        stats = Stats()
    )

    @Test
    fun convertsWorkerSnapshotsToEntityObservationsWithBaseUnits() {
        val observations = GbosObservations.from(report)
        val entity = observations.single { it.aggregationScope == "entity" }

        assert(entity.schemaVersion == "1.0.0")
        assert(entity.producer == GbosProducer(name = "info-test-process", version = "2.1.0"))
        assert(entity.scope == "jvm.process")
        assert(entity.attributes[GbosObservations.ATTR_PROCESS_PID] == GbosAttributeValue.Integer(13402L))
        assert(entity.attributes[GbosObservations.ATTR_PROCESS_ROLE] == GbosAttributeValue.Text("test-worker"))
        assert(entity.attributes[GbosObservations.ATTR_TASK_PATH] == GbosAttributeValue.Text(":core:test"))
        assert(entity.attributes[GbosObservations.ATTR_TEST_EXECUTOR] == GbosAttributeValue.Text("Gradle Test Executor 5"))
        assert(entity.attributes[GbosObservations.ATTR_GC_NAME] == GbosAttributeValue.Text("G1"))
        assert(entity.diagnostics.isEmpty())
        assert(!entity.partial)
        assert(entity.droppedObservations == null)

        assert(entity.measurement("jvm.process.memory.heap.limit", "last") == GbosMeasurement(
            name = "jvm.process.memory.heap.limit",
            value = 536_870_912.0,
            unit = "By",
            aggregation = "last"
        ))
        assert(entity.measurement("jvm.process.uptime", "last").unit == "s")
        assert(entity.measurement("jvm.process.uptime", "last").value == 1.2)
        assert(entity.measurement("jvm.process.cpu.time", "sum") == GbosMeasurement(
            name = "jvm.process.cpu.time",
            value = 3.05,
            unit = "s",
            aggregation = "sum"
        ))
        assert(entity.measurement("jvm.process.cpu.cores", "last").unit == "{core}")
        assert(entity.measurement("jvm.process.memory.heap.used", "last").unit == "By")
        assert(entity.measurement("jvm.process.memory.heap.peak", "max").value == 150_323_855.0)
        assert(entity.measurement("jvm.process.memory.metaspace.peak", "max").unit == "By")
        assert(entity.measurement("jvm.process.gc.collections", "count") == GbosMeasurement(
            name = "jvm.process.gc.collections",
            value = 2.0,
            unit = "{collection}",
            aggregation = "count"
        ))
        assert(entity.measurement("jvm.process.gc.time", "sum").unit == "s")
        assert(entity.measurement("jvm.process.jit.time", "sum").unit == "s")
        assert(entity.measurement("jvm.process.classes.loaded", "last").unit == "{class}")
        assert(entity.measurement("jvm.process.threads.peak", "max").unit == "{thread}")
    }

    @Test
    fun convertsAggregateSummaryToBuildLevelObservation() {
        val build = GbosObservations.from(report).single { it.aggregationScope == "build" }

        assert(build.scope == "jvm.process")
        assert(build.attributes.keys == setOf(GbosObservations.ATTR_PROCESS_ROLE))
        assert(build.attributes[GbosObservations.ATTR_PROCESS_ROLE] == GbosAttributeValue.Text("test-worker"))
        assert(build.measurement("jvm.process.cpu.cores", "max").unit == "{core}")
        assert(build.measurement("jvm.process.cpu.time", "sum").value == 3.05)
        assert(build.measurement("jvm.process.memory.heap.peak", "max").value == 150_323_855.0)
        assert(build.measurement("jvm.process.memory.metaspace.peak", "max").unit == "By")
        assert(build.measurement("jvm.process.jit.time", "sum").unit == "s")
        assert(build.measurement("jvm.process.jit.time", "max").unit == "s")
        assert(build.measurement("jvm.process.classes.loaded", "max").unit == "{class}")
        assert(build.measurement("jvm.process.gc.collections", "sum").unit == "{collection}")
        assert(build.measurement("jvm.process.uptime", "sum").unit == "s")
        assert(build.diagnostics.isEmpty())
        assert(build.droppedObservations == null)
    }

    @Test
    fun omitsMissingSnapshotSentinelsAndEmitsDiagnostics() {
        val missing = ReportDocument.from(
            processes = mapOf(
                13402L to TestProcess(task = ":core:test", executor = "Gradle Test Executor 5", max = "512m")
            ),
            runtimeStats = emptyMap(),
            stats = Stats(statsSnapshotsMissing = 1)
        )

        val observations = GbosObservations.from(missing)
        val entity = observations.single { it.aggregationScope == "entity" }

        assert(entity.measurements.map { it.name } == listOf("jvm.process.memory.heap.limit"))
        assert(entity.measurement("jvm.process.memory.heap.limit", "last").value == 536_870_912.0)
        assert(entity.attributes[GbosObservations.ATTR_GC_NAME] == null)
        assert(
            entity.diagnostics == listOf(
                GbosDiagnostic(code = "gbos.test_process.stats_snapshot_missing", severity = "warning")
            )
        )
        assert(observations.none { it.aggregationScope == "build" })
        assert(entity.measurements.none { it.value < 0.0 })
    }

    @Test
    fun ordersEntityObservationsByCpuThenPid() {
        val multi = ReportDocument.from(
            processes = mapOf(
                20L to TestProcess(task = ":a:test", executor = "Gradle Test Executor 1", max = "512m"),
                10L to TestProcess(task = ":b:test", executor = "Gradle Test Executor 2", max = "512m"),
                30L to TestProcess(task = ":c:test", executor = "Gradle Test Executor 3", max = "512m")
            ),
            runtimeStats = mapOf(
                20L to runtime(20L, cpuTimeMs = 2_000),
                10L to runtime(10L, cpuTimeMs = 3_000),
                30L to runtime(30L, cpuTimeMs = 3_000)
            ),
            stats = Stats()
        )

        val entityPids = GbosObservations.from(multi)
            .filter { it.aggregationScope == "entity" }
            .map { (it.attributes[GbosObservations.ATTR_PROCESS_PID] as GbosAttributeValue.Integer).value }

        assert(entityPids == listOf(10L, 30L, 20L))
    }

    @Test
    fun recordsDroppedObservationsWhenEntityCapIsExceeded() {
        val multi = ReportDocument.from(
            processes = mapOf(
                1L to TestProcess(task = ":a:test", executor = "Gradle Test Executor 1", max = "512m"),
                2L to TestProcess(task = ":b:test", executor = "Gradle Test Executor 2", max = "512m"),
                3L to TestProcess(task = ":c:test", executor = "Gradle Test Executor 3", max = "512m")
            ),
            runtimeStats = mapOf(
                1L to runtime(1L, cpuTimeMs = 3_000),
                2L to runtime(2L, cpuTimeMs = 2_000),
                3L to runtime(3L, cpuTimeMs = 1_000)
            ),
            stats = Stats()
        )

        val observations = GbosObservations.from(multi, maxEntityObservations = 1)
        assert(observations.count { it.aggregationScope == "entity" } == 1)
        val build = observations.single { it.aggregationScope == "build" }
        assert(build.partial)
        assert(build.droppedObservations == 2)
        assert(
            build.diagnostics == listOf(
                GbosDiagnostic(code = "gbos.limit.observations_dropped", severity = "warning")
            )
        )
    }

    private fun runtime(pid: Long, cpuTimeMs: Long) = WorkerRuntimeStats(
        pid = pid,
        uptimeMs = 1_000,
        cpuTimeMs = cpuTimeMs,
        usedHeapBytes = 100_000_000,
        peakHeapBytes = 150_323_855,
        peakMetaspaceBytes = 15_246_295,
        maxHeapBytes = 536_870_912,
        gcCollections = 2,
        gcTimeMs = 0,
        gcType = "G1",
        jitTimeMs = 2_280,
        classesLoaded = 3_503,
        peakThreads = 10
    )

    private fun GbosObservation.measurement(name: String, aggregation: String): GbosMeasurement =
        measurements.single { it.name == name && it.aggregation == aggregation }
}
