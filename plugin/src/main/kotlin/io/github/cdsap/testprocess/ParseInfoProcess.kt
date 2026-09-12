package io.github.cdsap.testprocess

import io.github.cdsap.testprocess.model.TestProcess

object ParseInfoProcess {
    const val TASK_PROPERTY = "io.github.cdsap.testprocess.task"
    private val TASK_PATTERN = Regex("-D${Regex.escape(TASK_PROPERTY)}=([^,\\s\\]]+)")

    fun get(processInfo: String): TestProcess? {
        if (!processInfo.contains("Gradle Test Executor") || !processInfo.contains("-Xmx")) return null
        val executor = getExecutor(processInfo)
        val task = getTask(processInfo)
        val heap = getHeapMemory(processInfo)
        if (executor.isEmpty() || task.isEmpty() || heap.isEmpty()) return null
        return TestProcess(task = task, executor = "Gradle Test Executor $executor", max = heap)
    }

    private fun getExecutor(processInfo: String): String {
        return if (processInfo.contains("Gradle Test Executor")) {
            processInfo.split("Gradle Test Executor ")[1].split("'")[0]
        } else ""
    }

    fun getTask(processInfo: String): String {
        return TASK_PATTERN.find(processInfo)?.groupValues?.get(1).orEmpty()
    }

    private fun getHeapMemory(processInfo: String) = processInfo.split("-Xmx")[1].split(",")[0]
}
