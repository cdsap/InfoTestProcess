package io.github.cdsap.testprocess.model

data class Stats(
    var jstatCalls: Int = 0,
    var totalProcesses: Int = 0,
    var tasksWithoutProcess: Int = 0,
    var jstatErrors: Int = 0
)
