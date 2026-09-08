package io.github.cdsap.testprocess.agent

import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.model.WorkerRuntimeStats
import org.junit.Test

class WorkerStateCollectorTest {
    @Test
    fun collectsStateWithCapturedAndMissingSnapshots() {
        val registryEntries = listOf(
            WorkerRegistryEntry(
                pid = 10,
                task = ":app:test",
                executor = "Gradle Test Executor 1",
                maxHeapBytes = 536870912,
                startMs = 1
            ),
            WorkerRegistryEntry(
                pid = 20,
                task = ":lib:test",
                executor = "Gradle Test Executor 2",
                maxHeapBytes = 1073741824,
                startMs = 2
            )
        )
        val runtimeStatsEntries = listOf(
            WorkerRuntimeStats(
                pid = 10,
                uptimeMs = 12000,
                cpuTimeMs = 3400,
                usedHeapBytes = 104857600,
                peakHeapBytes = 209715200,
                peakMetaspaceBytes = 52428800,
                maxHeapBytes = 536870912,
                gcCollections = 4,
                gcTimeMs = 120,
                gcType = "G1",
                jitTimeMs = 850,
                classesLoaded = 4823,
                peakThreads = 18
            )
        )

        val processes = mutableMapOf<Long, TestProcess>()
        val stats = Stats(totalProcesses = 2)
        val state = WorkerStateCollector.collect(registryEntries, runtimeStatsEntries, processes, stats)

        assert(state.processes.size == 2)
        assert(state.processes[10L]?.task == ":app:test")
        assert(state.processes[10L]?.max == "512m")
        assert(state.processes[20L]?.task == ":lib:test")
        assert(state.processes[20L]?.max == "1g")

        assert(state.runtimeStats.size == 1)
        assert(state.runtimeStats[10L]?.gcType == "G1")
        assert(20L !in state.runtimeStats)

        assert(stats.statsSnapshotsCaptured == 1)
        assert(stats.statsSnapshotsMissing == 1)
        assert(state.stats === stats)
        assert(state.processes === processes)
    }

    @Test
    fun doesNotOverwriteExistingProcessEntries() {
        val registryEntries = listOf(
            WorkerRegistryEntry(
                pid = 5,
                task = ":from:registry",
                executor = "Registry Executor",
                maxHeapBytes = 268435456,
                startMs = 1
            )
        )
        val existing = TestProcess(task = ":already:tracked", executor = "Existing", max = "256m")
        val processes = mutableMapOf(5L to existing)
        val stats = Stats()

        val state = WorkerStateCollector.collect(registryEntries, emptyList(), processes, stats)

        assert(state.processes[5L] === existing)
        assert(state.processes[5L]?.task == ":already:tracked")
        assert(stats.statsSnapshotsCaptured == 0)
        assert(stats.statsSnapshotsMissing == 1)
    }

    @Test
    fun mapsHeapBytesAndMissingExecutorFallback() {
        val registryEntries = listOf(
            WorkerRegistryEntry(
                pid = 7,
                task = ":a:test",
                maxHeapBytes = 536870912,
                startMs = 1
            ),
            WorkerRegistryEntry(
                pid = 8,
                task = ":b:test",
                executor = "Gradle Test Executor 2",
                maxHeapBytes = 1073741824,
                startMs = 2
            )
        )

        val processes = mutableMapOf<Long, TestProcess>()
        val state = WorkerStateCollector.collect(registryEntries, emptyList(), processes, Stats())

        assert(state.processes[7L]?.executor == "Gradle Test Executor pid-7")
        assert(state.processes[7L]?.max == "512m")
        assert(state.processes[8L]?.executor == "Gradle Test Executor 2")
        assert(state.processes[8L]?.max == "1g")
    }
}
