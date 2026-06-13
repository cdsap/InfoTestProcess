package io.github.cdsap.testprocess.service

import io.github.cdsap.testprocess.agent.WorkerRegistry
import io.github.cdsap.testprocess.agent.WorkerRuntimeStats
import io.github.cdsap.testprocess.model.PersistedState
import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.report.JsonValue
import io.github.cdsap.testprocess.report.OutputReport
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
        var registryDir: Provider<File>
        var agentJar: Provider<File>
        var develocity: Provider<Boolean>
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
        WorkerRegistry.read(registry).forEach { entry ->
            processes.putIfAbsent(entry.pid, entry.toTestProcess())
        }
        val runtimeStats: Map<Long, WorkerRuntimeStats> = WorkerRegistry.readStats(registry).associateBy { it.pid }
        stats.statsSnapshotsCaptured = runtimeStats.size
        stats.statsSnapshotsMissing = processes.keys.count { it !in runtimeStats }

        val payload = PersistedState(processes, runtimeStats, stats)
        if (parameters.develocity.get()) {
            val output = parameters.path.get()
            output.parentFile?.mkdirs()
            output.writeText(Json.encodeToString(PersistedState.serializer(), payload))
        } else {
            val outputJson = parameters.pathJson.get()
            outputJson.parentFile?.mkdirs()
            OutputReport(outputJson).extracted(processes, runtimeStats, stats, JsonValue())
        }
    }

    override fun onFinish(event: FinishEvent?) {
    }
}
