package io.github.cdsap.testprocess

import io.github.cdsap.testprocess.agent.WorkerRegistryEntry
import io.github.cdsap.testprocess.agent.WorkerStateCollector
import io.github.cdsap.testprocess.fake.FakeDevelocityPlugin
import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.model.WorkerRuntimeStats
import io.github.cdsap.testprocess.report.BuildScanData
import io.github.cdsap.testprocess.report.BuildScanReport
import io.github.cdsap.testprocess.report.OutputReport
import io.github.cdsap.testprocess.report.ReportDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties

/**
 * Characterization tests: pin what consumers of `io.github.cdsap.testprocess` observe today
 * (Build Scan custom values, tags, console output, `statsTestTasks.json`) so later migrations can
 * prove they are behaviour-preserving. Expected values are recorded from the current
 * implementation, not derived from a spec.
 *
 * Levels:
 * - Unit: the fixed worker set goes through [WorkerStateCollector] and [ReportDocument] exactly as
 *   `StatsReportPublisher` does, then into [BuildScanReport] with a recording [BuildScanData] sink
 *   and into [OutputReport]. Pins exact ordered keys/values/tags, the per-worker cap, GBOS
 *   observation structure and the `statsTestTasks.json` text.
 * - TestKit: wiring. A consumer task writes the same fixed worker set into the plugin's worker
 *   registry directory after `statsBuildService` has initialised it, so the real build-end
 *   pipeline (service close -> `statsTestTasks.txt` -> value source -> Develocity `buildFinished`)
 *   runs on deterministic input. No real test JVMs run, so no PID/timing normalisation is needed.
 *   Develocity is replaced by [FakeDevelocityPlugin], which prints `SCAN-VALUE`/`SCAN-TAG` lines.
 *   The only order that depends on the environment is the `workers` array in
 *   `statsTestTasks.json` (directory listing order), so TestKit compares it sorted by pid.
 */
class CharacterizationTest {
    @Rule
    @JvmField
    val testProjectDir = TemporaryFolder()

    // ---------------------------------------------------------------- unit level

    @Test
    fun fixedWorkerSetEmitsLegacySummaryWorkerValuesAndTags() {
        val sink = RecordingBuildScanData()

        BuildScanReport().extracted(fixtureReport(), sink)

        assertEquals(EXPECTED_LEGACY_SCAN_LINES, sink.lines)
    }

    @Test
    fun perWorkerValuesAreCappedAt950HeaviestCpuFirst() {
        val overCap = RecordingBuildScanData()
        BuildScanReport().extracted(syntheticReport(workers = 952), overCap)

        val keys = overCap.values.map { it.first }
        assertEquals(SUMMARY_KEYS, keys.take(12))
        assertEquals((952L downTo 3L).map { "testProcess.worker.$it" }, keys.subList(12, 962))
        assertEquals(
            listOf(
                "testProcess.worker.shown" to "950",
                "testProcess.worker.truncated" to "true",
                "testProcess.worker.total" to "952"
            ),
            overCap.values.drop(962)
        )
        assertEquals(965, keys.size)
        assertEquals(emptyList<String>(), overCap.tags)

        val atCap = RecordingBuildScanData()
        BuildScanReport().extracted(syntheticReport(workers = 950), atCap)
        assertEquals(963, atCap.values.size)
        assertEquals("testProcess.worker.shown" to "950", atCap.values.last())
        assertTrue(atCap.values.none { it.first == "testProcess.worker.truncated" || it.first == "testProcess.worker.total" })
    }

    @Test
    fun gbosOptInReplacesLegacyValuesWithGbosValuesAndKeepsTags() {
        val sink = RecordingBuildScanData()

        BuildScanReport(publishGbos = true).extracted(fixtureReport(), sink)

        assertEquals(
            listOf(
                "gbos.schema",
                "gbos.v1.producer.info_test_process.version",
                "gbos.v1.producer.info_test_process.name",
                OBSERVATION_KEY,
                OBSERVATION_KEY,
                OBSERVATION_KEY,
                OBSERVATION_KEY,
                "gbos.v1.index.info_test_process.jvm.process.cpu.cores.max",
                "gbos.v1.index.info_test_process.jvm.process.cpu.time.sum",
                "gbos.v1.index.info_test_process.jvm.process.memory.heap.peak.max"
            ),
            sink.values.map { it.first }
        )
        val scalars = sink.values.filter { it.first != OBSERVATION_KEY }.toMap()
        assertEquals("1.0.0", scalars["gbos.schema"])
        assertEquals("0.0.4", scalars["gbos.v1.producer.info_test_process.version"])
        assertEquals("info-test-process", scalars["gbos.v1.producer.info_test_process.name"])
        assertEquals("5", scalars["gbos.v1.index.info_test_process.jvm.process.cpu.cores.max"])
        assertEquals("303", scalars["gbos.v1.index.info_test_process.jvm.process.cpu.time.sum"])
        assertEquals("966367642", scalars["gbos.v1.index.info_test_process.jvm.process.memory.heap.peak.max"])
        assertEquals(EXPECTED_TAGS, sink.tags)

        val observations = sink.values.filter { it.first == OBSERVATION_KEY }.map { it.second.asJsonObject() }
        assertEquals(EXPECTED_GBOS_OBSERVATIONS, observations.map(::describeObservation))
    }

    @Test
    fun gbosObservationsAreCappedAndBuildObservationReportsDrops() {
        val sink = RecordingBuildScanData()

        BuildScanReport(publishGbos = true).extracted(syntheticReport(workers = 952), sink)

        val observations = sink.values.filter { it.first == OBSERVATION_KEY }.map { it.second.asJsonObject() }
        assertEquals(951, observations.size)
        assertEquals(
            (952L downTo 3L).toList(),
            observations.dropLast(1).map { it.getValue("attributes").jsonObject.getValue("process.pid").jsonPrimitive.long }
        )
        val build = observations.last()
        assertEquals(
            listOf("scope", "aggregationScope", "attributes", "measurements", "partial", "droppedObservations", "diagnostics"),
            build.keys.toList()
        )
        assertEquals("\"build\"", build.getValue("aggregationScope").toString())
        assertEquals("true", build.getValue("partial").toString())
        assertEquals("2", build.getValue("droppedObservations").toString())
        assertEquals(
            """[{"code":"gbos.limit.observations_dropped","severity":"warning"}]""",
            build.getValue("diagnostics").toString()
        )
    }

    @Test
    fun withoutDevelocityTheReportIsWrittenToStatsTestTasksJson() {
        val output = testProjectDir.newFile("statsTestTasks.json")

        OutputReport(output).write(fixtureReport())

        assertEquals(EXPECTED_STATS_TEST_TASKS_JSON, output.readText())
    }

    // ---------------------------------------------------------------- TestKit level

    @Test
    fun fakeDevelocityAppliedBeforeOrAfterThePluginEmitsIdenticalValues() {
        val before = consumerProject("before", listOf("com.gradle.develocity", "io.github.cdsap.testprocess"))
        val after = consumerProject("after", listOf("io.github.cdsap.testprocess", "com.gradle.develocity"))

        val beforeLines = pluginConsoleLines(run(before))
        val afterLines = pluginConsoleLines(run(after))

        assertEquals(EXPECTED_LEGACY_SCAN_LINES, beforeLines)
        assertEquals(beforeLines, afterLines)
        listOf(before, after).forEach { assertDevelocityPathConsumedPersistedState(it) }
    }

    @Test
    fun gbosOptInThroughDevelocityEmitsGbosValuesAndKeepsTags() {
        val project = consumerProject(
            "gbos",
            listOf("com.gradle.develocity", "io.github.cdsap.testprocess"),
            gradleProperties = "infoTestProcess.gbos.develocity.enabled=true"
        )

        val lines = pluginConsoleLines(run(project))

        val unitLevel = RecordingBuildScanData().also { BuildScanReport(publishGbos = true).extracted(fixtureReport(), it) }
        assertEquals(unitLevel.lines, lines)
        assertTrue(lines.none { it.startsWith("SCAN-VALUE testProcess.") })
        assertEquals(EXPECTED_TAGS.map { "SCAN-TAG $it" }, lines.filter { it.startsWith("SCAN-TAG ") })
    }

    @Test
    fun withoutDevelocityThePluginPrintsNothingAndWritesStatsTestTasksJson() {
        val project = consumerProject("no-develocity", listOf("io.github.cdsap.testprocess"))

        val lines = pluginConsoleLines(run(project))

        assertEquals(emptyList<String>(), lines)
        assertEquals(
            EXPECTED_STATS_TEST_TASKS_JSON.normalizedReport(),
            File(project, "build/info-test-process/statsTestTasks.json").readText().normalizedReport()
        )
        assertFalse(File(project, "build/info-test-process/statsTestTasks.txt").exists())
    }

    @Test
    fun configurationCacheStoreAndReuseEmitTheSameOutput() {
        val withDevelocity = consumerProject("cc-develocity", listOf("com.gradle.develocity", "io.github.cdsap.testprocess"))
        val stored = run(withDevelocity, "--configuration-cache")
        val reused = run(withDevelocity, "--configuration-cache")

        assertTrue(stored.output.contains("Configuration cache entry stored."))
        assertTrue(reused.output.contains("Configuration cache entry reused."))
        assertEquals(EXPECTED_LEGACY_SCAN_LINES, pluginConsoleLines(stored))
        assertEquals(pluginConsoleLines(stored), pluginConsoleLines(reused))
        assertDevelocityPathConsumedPersistedState(withDevelocity)

        val withoutDevelocity = consumerProject("cc-no-develocity", listOf("io.github.cdsap.testprocess"))
        val json = File(withoutDevelocity, "build/info-test-process/statsTestTasks.json")
        val storedNoDv = run(withoutDevelocity, "--configuration-cache")
        val storedJson = json.readText().normalizedReport()
        val reusedNoDv = run(withoutDevelocity, "--configuration-cache")

        assertTrue(storedNoDv.output.contains("Configuration cache entry stored."))
        assertTrue(reusedNoDv.output.contains("Configuration cache entry reused."))
        assertEquals(emptyList<String>(), pluginConsoleLines(storedNoDv))
        assertEquals(emptyList<String>(), pluginConsoleLines(reusedNoDv))
        assertEquals(EXPECTED_STATS_TEST_TASKS_JSON.normalizedReport(), storedJson)
        assertEquals(storedJson, json.readText().normalizedReport())
    }

    // ---------------------------------------------------------------- helpers

    private fun assertDevelocityPathConsumedPersistedState(project: File) {
        assertEquals(
            listOf("agent.jar", "workers"),
            File(project, "build/info-test-process").list()!!.sorted()
        )
    }

    private fun consumerProject(name: String, settingsPlugins: List<String>, gradleProperties: String = ""): File {
        val dir = testProjectDir.newFolder(name)
        File(dir, "settings.gradle").writeText(
            """
            |plugins {
            |${settingsPlugins.joinToString("\n") { "    id '$it'" }}
            |}
            |rootProject.name = 'characterization'
            |""".trimMargin()
        )
        File(dir, "gradle.properties").writeText(gradleProperties)
        val json = Json { encodeDefaults = true }
        val writes = FIXTURE_ENTRIES.map { "${it.pid}.json" to json.encodeToString(WorkerRegistryEntry.serializer(), it) } +
            FIXTURE_STATS.map { "${it.pid}.stats.json" to json.encodeToString(WorkerRuntimeStats.serializer(), it) }
        // Writes the fixed worker set after statsBuildService has reset the registry directory,
        // standing in for what the java agent writes from real test workers.
        File(dir, "build.gradle").writeText(
            """
            |def statsService = gradle.sharedServices.registrations.getByName('statsBuildService').service
            |def workersDir = layout.buildDirectory.dir('info-test-process/workers')
            |tasks.register('emitWorkers') {
            |    usesService(statsService)
            |    doLast {
            |        statsService.get()
            |        def dir = workersDir.get().asFile
            |        dir.mkdirs()
            |${writes.joinToString("\n") { (file, content) -> "        new File(dir, '$file').text = '$content'" }}
            |    }
            |}
            |""".trimMargin()
        )
        return dir
    }

    private fun run(project: File, vararg extraArgs: String): BuildResult =
        GradleRunner.create()
            .withProjectDir(project)
            .withPluginClasspath(testKitClasspath())
            .withGradleVersion(GRADLE_VERSION)
            .withArguments(listOf("emitWorkers") + extraArgs)
            .build()

    /**
     * The default plugin-under-test classpath, prefixed with the test resources (the
     * `com.gradle.develocity` descriptor pointing at [FakeDevelocityPlugin]) and test classes.
     * Prefixing makes the fake's descriptor win over the real Develocity jar's, which stays on
     * the classpath because the plugin and the fake compile against its API.
     */
    private fun testKitClasspath(): List<File> {
        val loader = CharacterizationTest::class.java.classLoader
        val metadata = Properties().apply {
            loader.getResourceAsStream("plugin-under-test-metadata.properties")!!.use { load(it) }
        }
        val pluginUnderTest = metadata.getProperty("implementation-classpath")
            .split(File.pathSeparator)
            .map(::File)
        val descriptor = loader.getResources(FAKE_DEVELOCITY_DESCRIPTOR).toList().single { it.protocol == "file" }
        val testResources = File(descriptor.toURI()).parentFile.parentFile.parentFile
        val testClasses = File(FakeDevelocityPlugin::class.java.protectionDomain.codeSource.location.toURI())
        return listOf(testResources, testClasses) + pluginUnderTest
    }

    /** Console lines contributed by the plugin (and the fake Develocity), minus Gradle's own chrome. */
    private fun pluginConsoleLines(result: BuildResult): List<String> =
        result.output.lines().filterNot { line -> line.isBlank() || GRADLE_CHROME.any { it.matches(line) } }

    private fun fixtureReport(): ReportDocument {
        val payload = WorkerStateCollector.collect(FIXTURE_ENTRIES, FIXTURE_STATS, emptyMap(), Stats())
        return ReportDocument.from(payload.processes, payload.runtimeStats, payload.stats)
    }

    /** Workers 1..n with distinct CPU time (pid * 1s) so the heaviest-first cap order is unambiguous. */
    private fun syntheticReport(workers: Int): ReportDocument {
        val pids = (1L..workers.toLong())
        return ReportDocument.from(
            processes = pids.associateWith { TestProcess(task = ":test", executor = "Gradle Test Executor $it", max = "") },
            runtimeStats = pids.associateWith { WorkerRuntimeStats(pid = it, uptimeMs = 1_000_000, cpuTimeMs = it * 1_000) },
            stats = Stats()
        )
    }

    private fun describeObservation(observation: JsonObject): List<String> = buildList {
        add("keys=${observation.keys.joinToString(",")}")
        add("scope=${observation.getValue("scope")}")
        add("aggregationScope=${observation.getValue("aggregationScope")}")
        observation.getValue("attributes").jsonObject.forEach { (key, value) -> add("attribute $key=$value") }
        observation.getValue("measurements").jsonArray.forEach { element ->
            val measurement = element.jsonObject
            assertEquals(listOf("name", "value", "unit", "aggregation"), measurement.keys.toList())
            add(
                "measurement ${measurement.getValue("name").jsonPrimitive.content} " +
                    "${measurement.getValue("value")} " +
                    "${measurement.getValue("unit").jsonPrimitive.content} " +
                    measurement.getValue("aggregation").jsonPrimitive.content
            )
        }
        (observation["diagnostics"] as? JsonArray)?.forEach { add("diagnostic ${it.jsonObject}") }
    }

    private fun String.asJsonObject(): JsonObject = Json.parseToJsonElement(this).jsonObject

    /** Parsed report with `workers` sorted by pid; JSON object equality ignores key order. */
    private fun String.normalizedReport(): JsonObject {
        val report = asJsonObject()
        val workers = report.getValue("workers").jsonArray
            .sortedBy { it.jsonObject.getValue("pid").jsonPrimitive.long }
        return JsonObject(report + ("workers" to JsonArray(workers)))
    }

    private class RecordingBuildScanData : BuildScanData {
        val lines = mutableListOf<String>()
        val values = mutableListOf<Pair<String, String>>()
        val tags = mutableListOf<String>()

        override fun value(key: String, value: String) {
            values += key to value
            lines += "SCAN-VALUE $key=$value"
        }

        override fun tag(name: String) {
            tags += name
            lines += "SCAN-TAG $name"
        }
    }

    private companion object {
        const val GRADLE_VERSION = "9.7.1"
        const val FAKE_DEVELOCITY_DESCRIPTOR = "META-INF/gradle-plugins/com.gradle.develocity.properties"
        const val OBSERVATION_KEY = "gbos.v1.producer.info_test_process.observation"

        val GRADLE_CHROME = listOf(
            Regex("""> Task :\S+.*"""),
            Regex("""BUILD SUCCESSFUL in .*"""),
            Regex("""\d+ actionable tasks?: .*"""),
            Regex("""Calculating task graph as .*"""),
            Regex("""Reusing configuration cache\."""),
            Regex("""Consider enabling configuration cache to speed up this build: .*"""),
            Regex("""Configuration cache entry (stored|reused)\.""")
        )

        // 41001: cpu-heavy (5 cores avg). 41002: near-oom (0.9g of 1g) and jit-bound (2s JIT of
        // 3s CPU). 41003: registered but no stats snapshot, with an empty executor name.
        val FIXTURE_ENTRIES = listOf(
            WorkerRegistryEntry(pid = 41001, task = ":app:test", executor = "Gradle Test Executor 1", maxHeapBytes = 536_870_912),
            WorkerRegistryEntry(pid = 41002, task = ":lib:test", executor = "Gradle Test Executor 2", maxHeapBytes = 1_073_741_824),
            WorkerRegistryEntry(pid = 41003, task = ":lib:test", executor = "", maxHeapBytes = 268_435_456)
        )
        val FIXTURE_STATS = listOf(
            WorkerRuntimeStats(
                pid = 41001, uptimeMs = 60_000, cpuTimeMs = 300_000, usedHeapBytes = 268_435_456,
                peakHeapBytes = 322_122_547, peakMetaspaceBytes = 104_857_600, maxHeapBytes = 536_870_912,
                gcCollections = 12, gcTimeMs = 1_500, gcType = "G1", jitTimeMs = 4_000,
                classesLoaded = 9_000, peakThreads = 40
            ),
            WorkerRuntimeStats(
                pid = 41002, uptimeMs = 120_000, cpuTimeMs = 3_000, usedHeapBytes = 536_870_912,
                peakHeapBytes = 966_367_642, peakMetaspaceBytes = 52_428_800, maxHeapBytes = 1_073_741_824,
                gcCollections = 3, gcTimeMs = 200, gcType = "Parallel", jitTimeMs = 2_000,
                classesLoaded = 4_000, peakThreads = 20
            )
        )

        val SUMMARY_KEYS = listOf(
            "testProcess.workers.count",
            "testProcess.workers.tasksWith",
            "testProcess.workers.snapshotsMissing",
            "testProcess.cpuCoresAvg.max",
            "testProcess.cpuTimeSec.sum",
            "testProcess.heapPeakGb.max",
            "testProcess.metaspacePeakMb.max",
            "testProcess.jitSec.sum",
            "testProcess.jitSec.max",
            "testProcess.classesLoaded.max",
            "testProcess.gcCollections.sum",
            "testProcess.uptimeMin.sum"
        )

        val EXPECTED_TAGS = listOf("tests:cpu-heavy", "tests:near-oom", "tests:jit-bound", "tests:no-snapshot")

        val EXPECTED_LEGACY_SCAN_LINES = listOf(
            "SCAN-VALUE testProcess.workers.count=3",
            "SCAN-VALUE testProcess.workers.tasksWith=2",
            "SCAN-VALUE testProcess.workers.snapshotsMissing=1",
            "SCAN-VALUE testProcess.cpuCoresAvg.max=5.0",
            "SCAN-VALUE testProcess.cpuTimeSec.sum=303.0",
            "SCAN-VALUE testProcess.heapPeakGb.max=0.9",
            "SCAN-VALUE testProcess.metaspacePeakMb.max=100.0",
            "SCAN-VALUE testProcess.jitSec.sum=6.0",
            "SCAN-VALUE testProcess.jitSec.max=4.0",
            "SCAN-VALUE testProcess.classesLoaded.max=9000",
            "SCAN-VALUE testProcess.gcCollections.sum=15",
            "SCAN-VALUE testProcess.uptimeMin.sum=3.0",
            """SCAN-VALUE testProcess.worker.41001={"pid":41001,"task":":app:test","executor":"Gradle Test Executor 1","xmx":"512m","uptimeMin":1.0,"cpuTimeSec":300.0,"cpuCoresAvg":5.0,"heapUsageGb":0.25,"heapPeakGb":0.3,"metaspacePeakMb":100.0,"gcType":"G1","gcCollections":12,"gcTimeSec":1.5,"jitSec":4.0,"classesLoaded":9000,"peakThreads":40,"statsSnapshotMissing":false}""",
            """SCAN-VALUE testProcess.worker.41002={"pid":41002,"task":":lib:test","executor":"Gradle Test Executor 2","xmx":"1g","uptimeMin":2.0,"cpuTimeSec":3.0,"cpuCoresAvg":0.03,"heapUsageGb":0.5,"heapPeakGb":0.9,"metaspacePeakMb":50.0,"gcType":"Parallel","gcCollections":3,"gcTimeSec":0.2,"jitSec":2.0,"classesLoaded":4000,"peakThreads":20,"statsSnapshotMissing":false}""",
            """SCAN-VALUE testProcess.worker.41003={"pid":41003,"task":":lib:test","executor":"Gradle Test Executor pid-41003","xmx":"256m","uptimeMin":0.0,"cpuTimeSec":0.0,"cpuCoresAvg":0.0,"heapUsageGb":0.0,"heapPeakGb":0.0,"metaspacePeakMb":0.0,"gcType":"Unknown","gcCollections":0,"gcTimeSec":0.0,"jitSec":0.0,"classesLoaded":-1,"peakThreads":-1,"statsSnapshotMissing":true}""",
            "SCAN-VALUE testProcess.worker.shown=3",
            "SCAN-TAG tests:cpu-heavy",
            "SCAN-TAG tests:near-oom",
            "SCAN-TAG tests:jit-bound",
            "SCAN-TAG tests:no-snapshot"
        )

        val EXPECTED_GBOS_OBSERVATIONS = listOf(
            listOf(
                "keys=scope,aggregationScope,attributes,measurements",
                "scope=\"jvm.process\"",
                "aggregationScope=\"entity\"",
                "attribute process.pid=41001",
                "attribute jvm.process.role=\"test-worker\"",
                "attribute gradle.task.path=\":app:test\"",
                "attribute gradle.test.executor=\"Gradle Test Executor 1\"",
                "attribute jvm.gc.name=\"G1\"",
                "measurement jvm.process.memory.heap.limit 536870912 By last",
                "measurement jvm.process.uptime 60 s last",
                "measurement jvm.process.cpu.time 300 s sum",
                "measurement jvm.process.cpu.cores 5 {core} last",
                "measurement jvm.process.memory.heap.used 268435456 By last",
                "measurement jvm.process.memory.heap.peak 322122547 By max",
                "measurement jvm.process.memory.metaspace.peak 104857600 By max",
                "measurement jvm.process.gc.collections 12 {collection} count",
                "measurement jvm.process.gc.time 1.5 s sum",
                "measurement jvm.process.jit.time 4 s sum",
                "measurement jvm.process.classes.loaded 9000 {class} last",
                "measurement jvm.process.threads.peak 40 {thread} max"
            ),
            listOf(
                "keys=scope,aggregationScope,attributes,measurements",
                "scope=\"jvm.process\"",
                "aggregationScope=\"entity\"",
                "attribute process.pid=41002",
                "attribute jvm.process.role=\"test-worker\"",
                "attribute gradle.task.path=\":lib:test\"",
                "attribute gradle.test.executor=\"Gradle Test Executor 2\"",
                "attribute jvm.gc.name=\"Parallel\"",
                "measurement jvm.process.memory.heap.limit 1073741824 By last",
                "measurement jvm.process.uptime 120 s last",
                "measurement jvm.process.cpu.time 3 s sum",
                "measurement jvm.process.cpu.cores 0.03 {core} last",
                "measurement jvm.process.memory.heap.used 536870912 By last",
                "measurement jvm.process.memory.heap.peak 966367642 By max",
                "measurement jvm.process.memory.metaspace.peak 52428800 By max",
                "measurement jvm.process.gc.collections 3 {collection} count",
                "measurement jvm.process.gc.time 0.2 s sum",
                "measurement jvm.process.jit.time 2 s sum",
                "measurement jvm.process.classes.loaded 4000 {class} last",
                "measurement jvm.process.threads.peak 20 {thread} max"
            ),
            listOf(
                "keys=scope,aggregationScope,attributes,measurements,diagnostics",
                "scope=\"jvm.process\"",
                "aggregationScope=\"entity\"",
                "attribute process.pid=41003",
                "attribute jvm.process.role=\"test-worker\"",
                "attribute gradle.task.path=\":lib:test\"",
                "attribute gradle.test.executor=\"Gradle Test Executor pid-41003\"",
                "measurement jvm.process.memory.heap.limit 268435456 By last",
                """diagnostic {"code":"gbos.test_process.stats_snapshot_missing","severity":"warning"}"""
            ),
            listOf(
                "keys=scope,aggregationScope,attributes,measurements",
                "scope=\"jvm.process\"",
                "aggregationScope=\"build\"",
                "attribute jvm.process.role=\"test-worker\"",
                "measurement jvm.process.cpu.cores 5 {core} max",
                "measurement jvm.process.cpu.time 303 s sum",
                "measurement jvm.process.memory.heap.peak 966367642 By max",
                "measurement jvm.process.memory.metaspace.peak 104857600 By max",
                "measurement jvm.process.jit.time 6 s sum",
                "measurement jvm.process.jit.time 4 s max",
                "measurement jvm.process.classes.loaded 9000 {class} max",
                "measurement jvm.process.gc.collections 15 {collection} sum",
                "measurement jvm.process.uptime 180 s sum"
            )
        )

        val EXPECTED_STATS_TEST_TASKS_JSON = """
            {
                "summary": {
                    "workers": {
                        "count": 3,
                        "tasksWith": 2,
                        "snapshotsMissing": 1
                    },
                    "cpuCoresAvgMax": 5.0,
                    "cpuTimeSecSum": 303.0,
                    "heapPeakGbMax": 0.9,
                    "metaspacePeakMbMax": 100.0,
                    "jitSecSum": 6.0,
                    "jitSecMax": 4.0,
                    "classesLoadedMax": 9000,
                    "gcCollectionsSum": 15,
                    "uptimeMinSum": 3.0
                },
                "byTask": {
                    ":app:test": {
                        "workers": 1,
                        "cpuCoresAvgMax": 5.0,
                        "cpuTimeSecSum": 300.0,
                        "heapPeakGbMax": 0.3,
                        "jitSecMax": 4.0,
                        "classesLoadedMax": 9000,
                        "peakThreadsMax": 40
                    },
                    ":lib:test": {
                        "workers": 1,
                        "cpuCoresAvgMax": 0.03,
                        "cpuTimeSecSum": 3.0,
                        "heapPeakGbMax": 0.9,
                        "jitSecMax": 2.0,
                        "classesLoadedMax": 4000,
                        "peakThreadsMax": 20
                    }
                },
                "workers": [
                    {
                        "pid": 41001,
                        "task": ":app:test",
                        "executor": "Gradle Test Executor 1",
                        "xmx": "512m",
                        "uptimeMin": 1.0,
                        "cpuTimeSec": 300.0,
                        "cpuCoresAvg": 5.0,
                        "heapUsageGb": 0.25,
                        "heapPeakGb": 0.3,
                        "metaspacePeakMb": 100.0,
                        "gcType": "G1",
                        "gcCollections": 12,
                        "gcTimeSec": 1.5,
                        "jitSec": 4.0,
                        "classesLoaded": 9000,
                        "peakThreads": 40,
                        "statsSnapshotMissing": false
                    },
                    {
                        "pid": 41002,
                        "task": ":lib:test",
                        "executor": "Gradle Test Executor 2",
                        "xmx": "1g",
                        "uptimeMin": 2.0,
                        "cpuTimeSec": 3.0,
                        "cpuCoresAvg": 0.03,
                        "heapUsageGb": 0.5,
                        "heapPeakGb": 0.9,
                        "metaspacePeakMb": 50.0,
                        "gcType": "Parallel",
                        "gcCollections": 3,
                        "gcTimeSec": 0.2,
                        "jitSec": 2.0,
                        "classesLoaded": 4000,
                        "peakThreads": 20,
                        "statsSnapshotMissing": false
                    },
                    {
                        "pid": 41003,
                        "task": ":lib:test",
                        "executor": "Gradle Test Executor pid-41003",
                        "xmx": "256m",
                        "uptimeMin": 0.0,
                        "cpuTimeSec": 0.0,
                        "cpuCoresAvg": 0.0,
                        "heapUsageGb": 0.0,
                        "heapPeakGb": 0.0,
                        "metaspacePeakMb": 0.0,
                        "gcType": "Unknown",
                        "gcCollections": 0,
                        "gcTimeSec": 0.0,
                        "jitSec": 0.0,
                        "classesLoaded": -1,
                        "peakThreads": -1,
                        "statsSnapshotMissing": true
                    }
                ],
                "tags": [
                    "tests:cpu-heavy",
                    "tests:near-oom",
                    "tests:jit-bound",
                    "tests:no-snapshot"
                ]
            }
        """.trimIndent()
    }
}
