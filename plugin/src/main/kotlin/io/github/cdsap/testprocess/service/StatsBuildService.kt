package io.github.cdsap.testprocess.service

import io.github.cdsap.testprocess.agent.AgentJarExtractor
import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.report.StatsOutputOptions
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.util.concurrent.ConcurrentHashMap

abstract class StatsBuildService : BuildService<StatsBuildService.Parameters>, AutoCloseable {
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
        AgentJarExtractor.extractAgentJar(agent, javaClass.classLoader)
    }

    override fun close() {
        StatsReportPublisher().publish(
            registryDir = parameters.registryDir.get().asFile,
            processes = processes,
            stats = stats,
            options = StatsOutputOptions(
                develocity = parameters.develocity.get(),
                persistedTxt = parameters.persistedTxt.get().asFile,
                persistedJson = parameters.persistedJson.get().asFile,
                gbosJson = parameters.gbosJson.get().asFile,
                gbosNdjson = parameters.gbosNdjson.get().asFile,
                writeGbosJson = parameters.gbosJsonOutput.get(),
                writeGbosNdjson = parameters.gbosNdjsonOutput.get()
            )
        )
    }
}
