package io.github.cdsap.testprocess

import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import io.github.cdsap.testprocess.report.BuildScanReport
import io.github.cdsap.testprocess.service.StatsBuildService
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.kotlin.dsl.of
import org.gradle.kotlin.dsl.withType
import java.io.File

class InfoTestProcessPlugin : Plugin<Settings> {
    override fun apply(target: Settings) {
        val develocityConfiguration = target.extensions.findByType(DevelocityConfiguration::class.java)
        // Compute paths only; do NOT touch the filesystem here — that would invalidate the
        // configuration cache between runs. The directories are created lazily by the
        // BuildService at execution time.
        val workDir = File("${target.rootDir}/.info-test-process")
        val agentJar = File(workDir, "agent.jar")
        val registryDir = File(workDir, "workers")

        val service = target.gradle.sharedServices.registerIfAbsent(
            "statsBuildService", StatsBuildService::class.java
        ) {
            parameters.path = target.providers.provider { File("${target.layout.rootDirectory}/statsTestTasks.txt") }
            parameters.pathJson = target.providers.provider { File("${target.layout.rootDirectory}/statsTestTasks.json") }
            parameters.registryDir = target.providers.provider { registryDir }
            parameters.agentJar = target.providers.provider { agentJar }
            parameters.develocity = target.providers.provider { develocityConfiguration != null }
        }

        val provider = target.providers.of(PersistedDeserializationValueSource::class) {
            parameters.file.set(File("${target.layout.rootDirectory}/statsTestTasks.txt"))
        }

        target.gradle.beforeProject {
            this.tasks.withType<Test>().configureEach {
                usesService(service)
                val testPath = this.path
                jvmArgs("-D${ParseInfoProcess.TASK_PROPERTY}=$testPath")
                jvmArgs("-javaagent:${agentJar.absolutePath}=${registryDir.absolutePath}")
                doFirst {
                    // Ensure the BuildService is initialized — its init block extracts the agent
                    // jar and resets the registry directory before any worker forks.
                    service.get()
                }
                addTestListener(object : TestListener {
                    override fun beforeSuite(suite: TestDescriptor?) {
                        if (isGradleExecutor(suite?.name)) {
                            service.get().stats.totalProcesses++
                        }
                    }
                    override fun afterSuite(suite: TestDescriptor?, result: TestResult?) {}
                    override fun beforeTest(testDescriptor: TestDescriptor?) {}
                    override fun afterTest(testDescriptor: TestDescriptor?, result: TestResult?) {}
                })
            }
        }
        if (develocityConfiguration != null) {
            BuildScanReport().develocityBuildScanReporting(develocityConfiguration, provider)
        }
    }

    private fun isGradleExecutor(name: String?): Boolean = name?.contains("Gradle Test Executor") ?: false
}
