package io.github.cdsap.testprocess.agent

import io.github.cdsap.testprocess.model.PersistedState
import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.model.WorkerRuntimeStats

object WorkerStateCollector {
    fun collect(
        registryEntries: Collection<WorkerRegistryEntry>,
        runtimeStatsEntries: Collection<WorkerRuntimeStats>,
        processes: MutableMap<Long, TestProcess>,
        stats: Stats
    ): PersistedState {
        registryEntries.forEach { entry ->
            processes.putIfAbsent(entry.pid, WorkerIdentityMapper.toTestProcess(entry))
        }
        val runtimeStats: Map<Long, WorkerRuntimeStats> =
            runtimeStatsEntries.associateBy { it.pid }
        stats.statsSnapshotsCaptured = runtimeStats.size
        stats.statsSnapshotsMissing = processes.keys.count { it !in runtimeStats }
        return PersistedState(processes, runtimeStats, stats)
    }
}
