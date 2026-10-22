package io.github.cdsap.testprocess.service

import io.github.cdsap.testprocess.agent.WorkerRegistrySnapshot
import java.io.File

fun interface WorkerStateReader {
    fun readSnapshot(registryDir: File): WorkerRegistrySnapshot
}
