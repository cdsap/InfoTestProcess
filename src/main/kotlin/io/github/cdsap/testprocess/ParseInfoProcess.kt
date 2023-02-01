package io.github.cdsap.testprocess

class ParseInfoProcess(private val rootDirPath: String) {

    fun get(processInfo: String): TestProcess? {
        return if (processInfo.contains("Gradle Test Executor") &&
            processInfo.contains("-Dorg.gradle.internal.worker.tmpdir=") &&
            processInfo.contains("-Xmx")
        ) {
            val executor = processInfo.split("Gradle Test Executor ")[1].split("'")[0]
            val task = processInfo
                .split("-Dorg.gradle.internal.worker.tmpdir=")[1].split(",")[0].replace("/build/tmp", "")
                .replace("/work", "").replace(rootDirPath, "")
                .replace("/", ":").split("\n")[0]
            val heap = processInfo.split("-Xmx")[1].split(",")[0]
            if (executor.isNotEmpty() && task.isNotEmpty() && heap.isNotEmpty()) {
                TestProcess(task = task, executor = "Gradle Test Executor $executor", max = heap)
            } else {
                null
            }
        } else {
            null
        }
    }
}
