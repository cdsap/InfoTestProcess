package io.github.cdsap.testprocess.agent

import io.github.cdsap.testprocess.model.TestProcess

internal object WorkerIdentityMapper {
    fun toTestProcess(entry: WorkerRegistryEntry): TestProcess = TestProcess(
        task = entry.task,
        executor = if (entry.executor.isNotEmpty()) entry.executor else "Gradle Test Executor pid-${entry.pid}",
        max = formatHeap(entry.maxHeapBytes)
    )

    private fun formatHeap(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes % (1024L * 1024 * 1024) == 0L -> "${bytes / (1024L * 1024 * 1024)}g"
        bytes >= 1024L * 1024 -> "${bytes / (1024L * 1024)}m"
        else -> "${bytes}b"
    }
}
