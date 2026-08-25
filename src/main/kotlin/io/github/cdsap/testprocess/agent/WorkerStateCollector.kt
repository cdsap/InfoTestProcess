package io.github.cdsap.testprocess.agent

import io.github.cdsap.testprocess.model.PersistedState
import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import java.io.File

object WorkerStateCollector {
    fun collect(
        registryDir: File,
        processes: MutableMap<Long, TestProcess>,
        stats: Stats
    ): PersistedState {
        WorkerRegistry.read(registryDir).forEach { entry ->
            processes.putIfAbsent(entry.pid, entry.toTestProcess())
        }
        val runtimeStats: Map<Long, WorkerRuntimeStats> =
            WorkerRegistry.readStats(registryDir).associateBy { it.pid }
        stats.statsSnapshotsCaptured = runtimeStats.size
        stats.statsSnapshotsMissing = processes.keys.count { it !in runtimeStats }
        return PersistedState(processes, runtimeStats, stats)
    }
}
