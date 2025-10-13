package io.github.cdsap.testprocess.service

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
        var develocity: Provider<Boolean>
    }

    val processes = mutableMapOf<Long, TestProcess>()
    val jstatResults = mutableMapOf<Long, String>()
    val stats = Stats()

    init {
        if (File(" ${parameters.path.get()}").exists()) {
            File(" ${parameters.path.get()}").deleteRecursively()
        }
    }

    override fun close() {
        if (parameters.develocity.get()) {
            val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
            val payload = PersistedState(processes, jstatResults, stats)
            val output = parameters.path.get()
            output.parentFile.mkdirs()
            output.writeText(json.encodeToString(PersistedState.serializer(), payload))
        } else {
            if (File(" ${parameters.pathJson.get()}").exists()) {
                File(" ${parameters.path.get()}").deleteRecursively()
            }
            val outputJson = parameters.pathJson.get()
            outputJson.parentFile.mkdirs()
            OutputReport(outputJson).extracted(processes, jstatResults, stats, JsonValue())

        }

    }


    override fun onFinish(event: FinishEvent?) {
    }
}
