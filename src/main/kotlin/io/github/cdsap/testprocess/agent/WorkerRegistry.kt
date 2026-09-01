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

object WorkerRegistry {
    private val json = Json { ignoreUnknownKeys = true }
    const val AGENT_RESOURCE = "META-INF/agent/info-test-process-agent.jar"
    private const val STATS_SUFFIX = ".stats.json"
    private const val IDENTITY_SUFFIX = ".json"

    fun read(dir: File): List<WorkerRegistryEntry> {
        if (!dir.isDirectory) return emptyList()
        val files = dir.listFiles { f ->
            f.isFile && f.name.endsWith(IDENTITY_SUFFIX) && !f.name.endsWith(STATS_SUFFIX)
        } ?: return emptyList()
        return files.mapNotNull { runCatching { json.decodeFromString<WorkerRegistryEntry>(it.readText()) }.getOrNull() }
    }

    fun readStats(dir: File): List<WorkerRuntimeStats> {
        if (!dir.isDirectory) return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(STATS_SUFFIX) } ?: return emptyList()
        return files.mapNotNull { runCatching { json.decodeFromString<WorkerRuntimeStats>(it.readText()) }.getOrNull() }
    }

    fun extractAgentJar(target: File, classLoader: ClassLoader): File? {
        if (target.exists() && target.length() > 0) return target
        val resource = classLoader.getResourceAsStream(AGENT_RESOURCE) ?: return null
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        resource.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        if (!tmp.renameTo(target)) tmp.copyTo(target, overwrite = true).also { tmp.delete() }
        return target
    }
}
