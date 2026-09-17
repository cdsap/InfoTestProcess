package io.github.cdsap.testprocess

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Regression for plugin application order with Develocity.
 *
 * When Build Scan reporting is wired, [StatsBuildService] writes
 * `statsTestTasks.txt` and [PersistedDeserializationValueSource] consumes
 * (deletes) it while publishing custom values. The file sink
 * `statsTestTasks.json` must not be used.
 *
 * If Develocity is missed due to apply-order probing, the service falls back
 * to the JSON sink and leaves that file behind.
 */
class IntegrationDvPluginOrderTest {
    @Rule
    @JvmField
    val testProjectDir = TemporaryFolder()

    @Test
    fun develocityReportingWorksWhenDevelocityIsAppliedAfterThisPlugin() {
        createProject(
            settingsPlugins = """
                plugins {
                    id 'io.github.cdsap.testprocess'
                    id 'com.gradle.develocity' version '4.1'
                }
            """.trimIndent()
        )

        assertDevelocityBuildScanReportingPath()
    }

    @Test
    fun develocityReportingWorksWhenDevelocityIsAppliedBeforeThisPlugin() {
        createProject(
            settingsPlugins = """
                plugins {
                    id 'com.gradle.develocity' version '4.1'
                    id 'io.github.cdsap.testprocess'
                }
            """.trimIndent()
        )

        assertDevelocityBuildScanReportingPath()
    }

    private fun assertDevelocityBuildScanReportingPath() {
        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments("test", "--configuration-cache")
            .withPluginClasspath()
            .withGradleVersion("9.7.1")
            .build()

        val workDir = File("${testProjectDir.root}/build/info-test-process")
        val txt = File(workDir, "statsTestTasks.txt")
        val json = File(workDir, "statsTestTasks.json")
        val agentJar = File(workDir, "agent.jar")

        assertTrue("StatsBuildService should have run", agentJar.exists())
        assertFalse(
            "File sink must not be used when Develocity Build Scan reporting is active",
            json.exists()
        )
        assertFalse(
            "Persisted DV state should be consumed by Build Scan ValueSource after custom values are published",
            txt.exists()
        )
    }

    private fun createProject(settingsPlugins: String) {
        testProjectDir.newFile("settings.gradle").appendText(
            """
                $settingsPlugins
                develocity {
                    server = "https://example.invalid"
                    buildScan {
                        publishing.onlyIf { false }
                    }
                }
            """.trimIndent()
        )
        testProjectDir.newFile("gradle.properties").appendText(
            """
                kotlin.internal.collectFUSMetrics=false
            """.trimIndent()
        )
        testProjectDir.newFile("build.gradle").appendText(
            """
                plugins {
                    id 'org.jetbrains.kotlin.jvm' version '2.2.0'
                    id 'application'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    testImplementation("org.jetbrains.kotlin:kotlin-test:2.2.20")
                }
            """.trimIndent()
        )
        val testPkgDir = File(testProjectDir.root, "src/test/kotlin/com/example")
        testPkgDir.mkdirs()
        File("${testPkgDir.path}/SamplePluginTest.kt").writeText(
            """
            package com.example

            import kotlin.test.Test
            import kotlin.test.assertTrue

            class SamplePluginTest {
                @Test
                fun `smoke test runs`() {
                    assertTrue(1 + 1 == 2)
                }
            }
            """.trimIndent()
        )
    }
}
