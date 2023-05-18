package io.github.cdsap.testprocess

import io.github.cdsap.testprocess.model.TestProcess

class ParseInfoProcess(private val rootDirPath: String) {

    fun get(processInfo: String): TestProcess? {
        return if (processInfo.contains("Gradle Test Executor") &&
            processInfo.contains("-Dorg.gradle.internal.worker.tmpdir=") &&
            processInfo.contains("-Xmx")
        ) {
            val executor = getExecutor(processInfo)
            val task = getTask(processInfo)
            val heap = getHeapMemory(processInfo)
            if (executor.isNotEmpty() && task.isNotEmpty() && heap.isNotEmpty()) {
                TestProcess(task = task, executor = "Gradle Test Executor $executor", max = heap)
            } else {
                null
            }
        } else {
            null
        }
    }

    private fun getExecutor(processInfo: String): String {
        return if (processInfo.contains("Gradle Test Executor")
        ) {
            val executor = processInfo.split("Gradle Test Executor ")[1].split("'")[0]
            executor
        } else ""
    }

    fun getTask(processInfo: String): String {
        return processInfo
            .split("-Dorg.gradle.internal.worker.tmpdir=")[1]
            .split(",")[0].replace("/build/tmp", "")
            .replace(rootDirPath, "").replace("/work", "")
            .replace("/", ":").split("\n")[0]

    }

    private fun getHeapMemory(processInfo: String) = processInfo.split("-Xmx")[1].split(",")[0]
}
