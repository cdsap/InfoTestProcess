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
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import java.io.File

abstract class StatsBuildService : BuildService<StatsBuildService.Parameters>, AutoCloseable,
    OperationCompletionListener {
    interface Parameters : BuildServiceParameters {
        var path: Provider<File>
        var pathJson: Provider<File>
        var pathGbosJson: Provider<File>
        var pathGbosNdjson: Provider<File>
        var registryDir: Provider<File>
        var agentJar: Provider<File>
        var develocity: Provider<Boolean>
        var gbosJsonOutput: Provider<Boolean>
        var gbosNdjsonOutput: Provider<Boolean>
    }

    val processes = mutableMapOf<Long, TestProcess>()
    val stats = Stats()

    init {
        // Prepare the agent + workers directory lazily on first task execution. Doing this
        // at plugin-apply time would create filesystem entries before the configuration
        // cache fingerprints, invalidating CC reuse across subsequent builds.
        val registry = parameters.registryDir.get()
        if (registry.exists()) registry.deleteRecursively()
        registry.mkdirs()
        val agent = parameters.agentJar.get()
        agent.parentFile?.mkdirs()
        WorkerRegistry.extractAgentJar(agent, javaClass.classLoader)
    }

    override fun close() {
        val registry = parameters.registryDir.get()
        val payload = WorkerStateCollector.collect(
            WorkerRegistry.read(registry),
            WorkerRegistry.readStats(registry),
            processes,
            stats
        )
        if (parameters.develocity.get()) {
            val output = parameters.path.get()
            output.parentFile?.mkdirs()
            output.writeText(Json.encodeToString(PersistedState.serializer(), payload))
        } else {
            val outputJson = parameters.pathJson.get()
            outputJson.parentFile?.mkdirs()
            OutputReport(outputJson).write(
                payload.processes,
                payload.runtimeStats,
                payload.stats
            )
        }
        val writeGbosJson = parameters.gbosJsonOutput.get()
        val writeGbosNdjson = parameters.gbosNdjsonOutput.get()
        if (writeGbosJson || writeGbosNdjson) {
            GbosOutputReport(parameters.pathGbosJson.get(), parameters.pathGbosNdjson.get()).write(
                ReportDocument.from(payload.processes, payload.runtimeStats, payload.stats),
                writeJson = writeGbosJson,
                writeNdjson = writeGbosNdjson
            )
        } else {
            parameters.pathGbosJson.get().delete()
            parameters.pathGbosNdjson.get().delete()
        }
    }

    override fun onFinish(event: FinishEvent?) {
    }
}
