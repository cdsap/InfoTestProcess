package io.github.cdsap.testprocess.agent

import io.github.cdsap.testprocess.model.TestProcess
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
) {
    fun toTestProcess(): TestProcess = TestProcess(
        task = task,
        executor = if (executor.isNotEmpty()) executor else "Gradle Test Executor pid-$pid",
        max = formatHeap(maxHeapBytes)
    )

    private fun formatHeap(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes % (1024L * 1024 * 1024) == 0L -> "${bytes / (1024L * 1024 * 1024)}g"
        bytes >= 1024L * 1024 -> "${bytes / (1024L * 1024)}m"
        else -> "${bytes}b"
    }
}

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
