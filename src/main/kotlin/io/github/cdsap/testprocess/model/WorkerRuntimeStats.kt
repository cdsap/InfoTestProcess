package io.github.cdsap.testprocess.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkerRuntimeStats(
    val pid: Long,
    val uptimeMs: Long = 0,
    val cpuTimeMs: Long = -1,
    val usedHeapBytes: Long = 0,
    val peakHeapBytes: Long = 0,
    val peakMetaspaceBytes: Long = 0,
    val maxHeapBytes: Long = 0,
    val gcCollections: Long = 0,
    val gcTimeMs: Long = 0,
    val gcType: String = "Unknown",
    val jitTimeMs: Long = -1,
    val classesLoaded: Long = -1,
    val peakThreads: Int = -1
)
