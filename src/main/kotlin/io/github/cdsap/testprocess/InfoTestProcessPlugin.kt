package io.github.cdsap.testprocess

import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import io.github.cdsap.testprocess.model.Stats
import io.github.cdsap.testprocess.model.TestProcess
import io.github.cdsap.testprocess.report.BuildScanReport
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.kotlin.dsl.withType
import java.lang.RuntimeException

class InfoTestProcessPlugin : Plugin<Project> {
    val processes = mutableMapOf<Long, TestProcess>()
    private val jstatResults = mutableMapOf<Long, String>()
    private val commandExecutor = CommandExecutor()
    val stats = Stats()
    override fun apply(target: Project) {
        val rootDirPath = target.rootDir.path.toString()
        target.allprojects {
            tasks.withType<Test>().configureEach {
                val testPath = this.path
                addTestListener(object : TestListener {
                    override fun beforeSuite(suite: TestDescriptor?) {

                        if (isGradleExecutor(suite?.name)) {
                            stats.totalProcesses++
                            registerProcess(rootDirPath, processes)
                        }
                    }

                    override fun afterSuite(suite: TestDescriptor?, result: TestResult?) {}

                    override fun beforeTest(testDescriptor: TestDescriptor?) {
                        parseProcessByTaskType(rootDirPath, testPath)
                    }

                    override fun afterTest(testDescriptor: TestDescriptor?, result: TestResult?) {}
                })
            }
        }
        target.gradle.rootProject {
            val develocityConfiguration = extensions.findByType(DevelocityConfiguration::class.java)
            val buildScanExtension = extensions.findByType(com.gradle.scan.plugin.BuildScanExtension::class.java)
            if (develocityConfiguration != null) {
                BuildScanReport().develocityBuildScanReporting(develocityConfiguration,processes,jstatResults,stats)
            }
            else if (buildScanExtension != null) {
               BuildScanReport().gradleEnterpriseBuildScanReporting(buildScanExtension,processes,jstatResults,stats)
            }
        }
    }

    private fun parseProcessByTaskType(rootDirPath: String, testPath: String) {
        try {
            ProcessHandle.current().descendants()
                .filter { isGradleExecutor(it.info().toString()) }
                .filter { ParseInfoProcess(rootDirPath).getTask(it.info().toString()) == testPath }
                .forEach {
                    stats.jstatCalls++
                    val a = commandExecutor.execute("jstat -gc -t  ${it.pid()}")
                    if (!a.toString().contains("MonitorException")) {
                        jstatResults[it.pid()] = a.toString()
                    } else {
                        stats.jstatErrors++
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
