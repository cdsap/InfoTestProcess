package io.github.cdsap.testprocess

import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.report.BuildScanReport
import io.github.cdsap.testprocess.service.StatsBuildService
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.kotlin.dsl.of
import org.gradle.kotlin.dsl.withType
import java.io.File
import java.lang.RuntimeException

class InfoTestProcessPlugin : Plugin<Settings> {
    val commandExecutor = CommandExecutor()
    override fun apply(target: Settings) {
        val rootDirPath = target.rootDir.path.toString()

        val hasDevelocity = try {
            Class.forName("com.gradle.develocity.agent.gradle.DevelocityConfiguration")
            true
        } catch (_: ClassNotFoundException) {
            false
        }

        val service = target.gradle.sharedServices.registerIfAbsent(
            "statsBuildService", StatsBuildService::class.java
        ) {
            parameters.path = target.providers.provider { File("${target.layout.rootDirectory}/statsTestTasks.txt") }
            parameters.pathJson =
                target.providers.provider { File("${target.layout.rootDirectory}/statsTestTasks.json") }
            parameters.develocity = target.providers.provider { hasDevelocity }
        }

        val provider = target.providers.of(PersistedDeserializationValueSource::class) {
            parameters.file.set(File("${target.layout.rootDirectory}/statsTestTasks.txt"))

        }
        target.gradle.beforeProject {

            this.tasks.withType<Test>().configureEach {
                usesService(service)
                val testPath = this.path
                addTestListener(object : TestListener {
                    override fun beforeSuite(suite: TestDescriptor?) {
                        if (isGradleExecutor(suite?.name)) {
                            service.get().stats.totalProcesses++
                            registerProcess(rootDirPath, service.get().processes)
                        }
                    }

                    override fun afterSuite(suite: TestDescriptor?, result: TestResult?) {}

                    override fun beforeTest(testDescriptor: TestDescriptor?) {
                        parseProcessByTaskType(rootDirPath, testPath, service)
                    }

                    override fun afterTest(testDescriptor: TestDescriptor?, result: TestResult?) {}
                })

            }

        }
        if (hasDevelocity && target.extensions.findByType(DevelocityConfiguration::class.java) != null) {
            BuildScanReport().develocityBuildScanReporting(
                target.extensions.findByType(DevelocityConfiguration::class.java)!!,
                provider
            )
        }
    }

    private fun parseProcessByTaskType(rootDirPath: String, testPath: String, service: Provider<StatsBuildService>) {
        try {
            ProcessHandle.current().descendants()
                .filter { isGradleExecutor(it.info().toString()) }
                .filter { ParseInfoProcess(rootDirPath).getTask(it.info().toString()) == testPath }
                .forEach {
                    service.get().stats.jstatCalls++
                    val a = commandExecutor.execute("jstat -gc -t  ${it.pid()}")
                    if (!a.toString().contains("MonitorException")) {
                        service.get().jstatResults[it.pid()] = a.toString()
                    } else {
                        service.get().stats.jstatErrors++
                    }
                }
        } catch (e: RuntimeException) {
        }
    }


    fun registerProcess(rootDirPath: String, processes: MutableMap<Long, TestProcess>) {
        try {
            val parseInfoProcess = ParseInfoProcess(rootDirPath)
            ProcessHandle.current().descendants().forEach {
                if (isGradleExecutor(it.info().toString()) && !processes.containsKey(it.pid())) {
                    val process = parseInfoProcess.get(it.info().toString())
                    if (process != null) {
                        processes[it.pid()] = process
                    }
                }
            }
        } catch (e: RuntimeException) {
        }
    }

    fun isGradleExecutor(name: String?): Boolean = name?.contains("Gradle Test Executor") ?: false

}
