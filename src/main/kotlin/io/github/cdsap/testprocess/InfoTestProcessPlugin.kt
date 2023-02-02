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
                buildScanReporting(buildScanExtension, processes)
            }
        }
    }

    private fun buildScanReporting(
        buildScanExtension: BuildScanExtension,
        processes: MutableMap<Long, TestProcess>
    ) {

        buildScanExtension.buildFinished {
            if (processes.isNotEmpty()) {
                buildScanExtension.value("Test-Process-Summary", "${processes.count()} processes created")

                processes.map {
                    buildScanExtension.value(
                        "Test-Process-pid-${it.key}",
                        "[${it.value.executor}, ${it.value.task}, ${it.value.max}]"
                    )
                }

                processes.entries.groupBy { it.value.task }.forEach {
                    if (it.value.count() > 1) {
                        buildScanExtension.value(
                            "Test-Process-${it.key}",
                            "crated ${it.value.count()} processes"
                        )
                    }
                }
            }
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
