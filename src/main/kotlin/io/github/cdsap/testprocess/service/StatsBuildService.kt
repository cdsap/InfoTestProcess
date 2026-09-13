package io.github.cdsap.testprocess.service

import io.github.cdsap.testprocess.agent.WorkerRegistry
import io.github.cdsap.testprocess.agent.WorkerStateCollector
import io.github.cdsap.testprocess.model.PersistedState
import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.report.GbosOutputReport
import io.github.cdsap.testprocess.report.OutputReport
import io.github.cdsap.testprocess.report.ReportDocument
import kotlinx.serialization.json.Json
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import java.util.concurrent.ConcurrentHashMap

abstract class StatsBuildService : BuildService<StatsBuildService.Parameters>, AutoCloseable,
    OperationCompletionListener {
    interface Parameters : BuildServiceParameters {
        val persistedTxt: RegularFileProperty
        val persistedJson: RegularFileProperty
        val gbosJson: RegularFileProperty
        val gbosNdjson: RegularFileProperty
        val registryDir: DirectoryProperty
        val agentJar: RegularFileProperty
        val develocity: Property<Boolean>
        val gbosJsonOutput: Property<Boolean>
        val gbosNdjsonOutput: Property<Boolean>
    }

    val processes = ConcurrentHashMap<Long, TestProcess>()
    val stats = Stats()

    init {
        // Prepare the agent + workers directory lazily on first task execution. Doing this
        // at plugin-apply time would create filesystem entries before the configuration
        // cache fingerprints, invalidating CC reuse across subsequent builds.
        val registry = parameters.registryDir.get().asFile
        if (registry.exists()) registry.deleteRecursively()
        registry.mkdirs()
        val agent = parameters.agentJar.get().asFile
        agent.parentFile?.mkdirs()
        WorkerRegistry.extractAgentJar(agent, javaClass.classLoader)
    }

    override fun close() {
        val registry = parameters.registryDir.get().asFile
        val payload = WorkerStateCollector.collect(
            WorkerRegistry.read(registry),
            WorkerRegistry.readStats(registry),
            processes,
            stats
        )
        val report = ReportDocument.from(payload.processes, payload.runtimeStats, payload.stats)
        if (parameters.develocity.get()) {
            val output = parameters.persistedTxt.get().asFile
            output.parentFile?.mkdirs()
            output.writeText(Json.encodeToString(PersistedState.serializer(), payload))
        } else {
            val outputJson = parameters.persistedJson.get().asFile
            outputJson.parentFile?.mkdirs()
            OutputReport(outputJson).write(report)
        }
        val writeGbosJson = parameters.gbosJsonOutput.get()
        val writeGbosNdjson = parameters.gbosNdjsonOutput.get()
        if (writeGbosJson || writeGbosNdjson) {
            GbosOutputReport(
                parameters.gbosJson.get().asFile,
                parameters.gbosNdjson.get().asFile
            ).write(
                report,
                writeJson = writeGbosJson,
                writeNdjson = writeGbosNdjson
            )
        } else {
            parameters.gbosJson.get().asFile.delete()
            parameters.gbosNdjson.get().asFile.delete()
        }
    }

    override fun onFinish(event: FinishEvent?) {
    }
}
