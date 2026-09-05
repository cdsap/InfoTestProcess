package io.github.cdsap.testprocess.model

import kotlinx.serialization.Serializable

@Serializable
data class Stats(
    var totalProcesses: Int = 0,
    var tasksWithoutProcess: Int = 0,
    var statsSnapshotsCaptured: Int = 0,
    var statsSnapshotsMissing: Int = 0
)
