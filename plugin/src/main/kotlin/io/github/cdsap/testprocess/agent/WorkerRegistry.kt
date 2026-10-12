package io.github.cdsap.testprocess.agent

import io.github.cdsap.testprocess.model.WorkerRuntimeStats
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class WorkerRegistryEntry(
    val pid: Long,
    val task: String,
    val executor: String = "",
    val maxHeapBytes: Long = 0,
    val startMs: Long = 0,
    val args: List<String> = emptyList()
)

data class WorkerRegistrySnapshot(
    val identityEntries: List<WorkerRegistryEntry>,
    val runtimeStats: List<WorkerRuntimeStats>
)

object WorkerRegistry {
    private val json = Json { ignoreUnknownKeys = true }
    private const val STATS_SUFFIX = ".stats.json"
    private const val IDENTITY_SUFFIX = ".json"

    fun readSnapshot(dir: File): WorkerRegistrySnapshot = WorkerRegistrySnapshot(
        identityEntries = readIdentityEntries(dir),
        runtimeStats = readRuntimeStats(dir)
    )

    private fun readIdentityEntries(dir: File): List<WorkerRegistryEntry> {
        if (!dir.isDirectory) return emptyList()
        val files = dir.listFiles { f ->
            f.isFile && f.name.endsWith(IDENTITY_SUFFIX) && !f.name.endsWith(STATS_SUFFIX)
        } ?: return emptyList()
        return files.mapNotNull { runCatching { json.decodeFromString<WorkerRegistryEntry>(it.readText()) }.getOrNull() }
    }

    private fun readRuntimeStats(dir: File): List<WorkerRuntimeStats> {
        if (!dir.isDirectory) return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(STATS_SUFFIX) } ?: return emptyList()
        return files.mapNotNull { runCatching { json.decodeFromString<WorkerRuntimeStats>(it.readText()) }.getOrNull() }
    }
}
