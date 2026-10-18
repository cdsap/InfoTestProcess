package io.github.cdsap.testprocess.agent

import java.io.File

fun interface WorkerStateReader {
    fun readSnapshot(registryDir: File): WorkerRegistrySnapshot
}
