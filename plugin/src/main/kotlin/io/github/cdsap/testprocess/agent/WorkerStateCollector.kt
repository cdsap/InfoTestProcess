package io.github.cdsap.testprocess.agent

import io.github.cdsap.testprocess.model.PersistedState
import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.model.WorkerRuntimeStats

object WorkerStateCollector {
    fun collect(
        registryEntries: Collection<WorkerRegistryEntry>,
        runtimeStatsEntries: Collection<WorkerRuntimeStats>,
        processes: Map<Long, TestProcess>,
        stats: Stats
    ): PersistedState {
        val processesSnapshot = processes.toMutableMap()
        registryEntries.forEach { entry ->
            processesSnapshot.putIfAbsent(entry.pid, WorkerIdentityMapper.toTestProcess(entry))
        }
        val runtimeStats: Map<Long, WorkerRuntimeStats> =
            runtimeStatsEntries.associateBy { it.pid }
        val statsSnapshot = stats.copy(
            statsSnapshotsCaptured = runtimeStats.size,
            statsSnapshotsMissing = processesSnapshot.keys.count { it !in runtimeStats }
        )
        return PersistedState(processesSnapshot, runtimeStats, statsSnapshot)
    }
}
