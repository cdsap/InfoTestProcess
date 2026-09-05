package io.github.cdsap.testprocess.model

import kotlinx.serialization.Serializable

@Serializable
data class PersistedState(
    val processes: Map<Long, TestProcess>,
    val runtimeStats: Map<Long, WorkerRuntimeStats>,
    val stats: Stats
)
