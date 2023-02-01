package io.github.cdsap.testprocess

import com.gradle.scan.plugin.BuildScanExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.kotlin.dsl.withType

class InfoTestProcessPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val rootDirPath = target.rootDir.path.toString()
        val processes = mutableMapOf<Long, TestProcess>()
        target.allprojects {


            tasks.withType<Test>().configureEach {
                addTestListener(object : TestListener {
                    override fun beforeSuite(suite: TestDescriptor?) {}

                    override fun afterSuite(suite: TestDescriptor?, result: TestResult?) {
                        if (suite?.name?.contains("Gradle Test Executor") == true) {
                            parseProcesses(rootDirPath, processes)
                        }
                    }

                    override fun beforeTest(testDescriptor: TestDescriptor?) {}

                    override fun afterTest(testDescriptor: TestDescriptor?, result: TestResult?) {}

                })
            }
        }
        target.gradle.rootProject {
            val buildScanExtension = extensions.findByType(com.gradle.scan.plugin.BuildScanExtension::class.java)
            if (buildScanExtension != null) {
                buildScanReporting(project, buildScanExtension)
            } else {

            }
        }
    }

    private fun buildScanReporting(
        project: Project,
        buildScanExtension: BuildScanExtension
    ) {

        buildScanExtension.buildFinished {
        }
    }

    fun parseProcesses(rootDirPath: String, processes: MutableMap<Long, TestProcess>) {
        val parseInfoProcess = ParseInfoProcess(rootDirPath)
        ProcessHandle.current().descendants().forEach {
            if (it.info().toString().contains("Gradle Test Executor") && !processes.containsKey(it.pid())) {
                val process = parseInfoProcess.get(it.info().toString())
                if (process != null) {
                    processes[it.pid()] = process
                }
            }
        }
    }
}
