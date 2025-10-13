package io.github.cdsap.testprocess.model

import kotlinx.serialization.Serializable

@Serializable
data class PersistedState(
    val processes: Map<Long, TestProcess>,
    val jstatResults: Map<Long, String>,
    val stats: Stats
)
