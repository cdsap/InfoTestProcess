package io.github.cdsap.testprocess

import io.github.cdsap.testprocess.report.BuildScanReport
import io.github.cdsap.testprocess.service.StatsBuildService
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.kotlin.dsl.of
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider

class InfoTestProcessPlugin : Plugin<Settings> {
    override fun apply(target: Settings) {
        val develocityConfiguration = target.extensions.findByName("develocity")
        val develocityOnClasspath = develocityConfiguration != null
        val infoTestProcess = target.extensions.create(
            "infoTestProcess",
            InfoTestProcessExtension::class.java
        )
        DevelocityReporting.configureConventions(
            infoTestProcess.develocity,
            target.providers,
            develocityOnClasspath
        )
        GbosOptIn.configureConventions(infoTestProcess.gbos, target.providers)

        // Populated by the rootProject {} action below, which runs when the root project is
        // instantiated — before any project (including the root) is configured, so the
        // per-Test wiring is always in place before a test task is reached.
        var wireProject: (Project) -> Unit = {}

        // Registered from Settings scope, not from inside rootProject {}: Gradle.beforeProject
        // is a Gradle-wide (cross-project) hook, and since Isolated Projects graduated to
        // incubating in Gradle 9.7 reaching for it from a project fails the build with
        // "Project ':' cannot access Gradle.beforeProject".
        target.gradle.beforeProject { wireProject(this) }

        // Defer to the root Project so we can use ProjectLayout.getBuildDirectory()
        // (https://docs.gradle.org/current/dsl/org.gradle.api.file.ProjectLayout.html) —
        // a DirectoryProperty that respects any custom buildDirectory configuration and
        // composes through the typed Directory / RegularFile / Provider API end-to-end.
        target.gradle.rootProject {
            val workDir = layout.buildDirectory.dir("info-test-process")
            val agentJar = workDir.map { it.file("agent.jar") }
            val registryDir = workDir.map { it.dir("workers") }
            val persistedTxt = workDir.map { it.file("statsTestTasks.txt") }
            val persistedJson = workDir.map { it.file("statsTestTasks.json") }
            val gbosJson = workDir.map { it.file("gbos.json") }
            val gbosNdjson = workDir.map { it.file("gbos.ndjson") }

            val service = gradle.sharedServices.registerIfAbsent(
                "statsBuildService", StatsBuildService::class.java
            ) {
                parameters.path = persistedTxt.map { it.asFile }
                parameters.pathJson = persistedJson.map { it.asFile }
                parameters.pathGbosJson = gbosJson.map { it.asFile }
                parameters.pathGbosNdjson = gbosNdjson.map { it.asFile }
                parameters.registryDir = registryDir.map { it.asFile }
                parameters.agentJar = agentJar.map { it.asFile }
                parameters.develocity = providers.provider {
                    DevelocityReporting.reportToDevelocity(
                        develocityOnClasspath,
                        infoTestProcess.develocity.enabled.get()
                    )
                }
                parameters.gbosJsonOutput = infoTestProcess.gbos.json
                parameters.gbosNdjsonOutput = infoTestProcess.gbos.ndjson
            }

            val persistedStateProvider = providers.of(PersistedDeserializationValueSource::class) {
                parameters.file.set(persistedTxt)
            }
            if (develocityOnClasspath && infoTestProcess.develocity.enabled.get()) {
                @Suppress("UNCHECKED_CAST")
                BuildScanReport(infoTestProcess.gbos.develocity.get())
                    .develocityBuildScanReporting(
                        develocityConfiguration as com.gradle.develocity.agent.gradle.DevelocityConfiguration,
                        persistedStateProvider
                    )
            }

            wireProject = { project ->
                project.configureTestTasks(service, agentJar, registryDir)
            }
        }
    }

    private fun Project.configureTestTasks(
        service: Provider<StatsBuildService>,
        agentJar: Provider<RegularFile>,
        registryDir: Provider<Directory>
    ) {
        tasks.withType<Test>().configureEach {
            usesService(service)
            val testPath = path
            // Use a CommandLineArgumentProvider so the paths resolve at task
            // execution time — after any custom buildDirectory configuration in
            // the root build script has been applied.
            jvmArgumentProviders.add(CommandLineArgumentProvider {
                listOf(
                    "-D${ParseInfoProcess.TASK_PROPERTY}=$testPath",
                    "-javaagent:${agentJar.get().asFile.absolutePath}=${registryDir.get().asFile.absolutePath}"
                )
            })
            doFirst {
                // Materializing the service forces its init block, which extracts
                // the agent jar and resets the registry directory before workers fork.
                service.get()
            }
            addTestListener(object : TestListener {
                override fun beforeSuite(suite: TestDescriptor?) {
                    if (isGradleExecutor(suite?.name)) {
                        service.get().stats.incrementTotalProcesses()
                    }
                }
                override fun afterSuite(suite: TestDescriptor?, result: TestResult?) {}
                override fun beforeTest(testDescriptor: TestDescriptor?) {}
                override fun afterTest(testDescriptor: TestDescriptor?, result: TestResult?) {}
            })
        }
    }

    private fun isGradleExecutor(name: String?): Boolean = name?.contains("Gradle Test Executor") ?: false
}
