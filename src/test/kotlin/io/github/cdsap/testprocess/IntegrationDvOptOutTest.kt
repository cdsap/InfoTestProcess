package io.github.cdsap.testprocess

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class IntegrationDvOptOutTest {
    @Rule
    @JvmField
    val testProjectDir = TemporaryFolder()

    @Test
    fun develocityOptOutWritesJsonInsteadOfBuildScanPersistence() {
        createProject(
            extraGradleProperties = "infoTestProcess.develocity.enabled=false"
        )

        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments("test", "--configuration-cache")
            .withPluginClasspath()
            .withGradleVersion("9.7.1")
            .build()

        val json = File("${testProjectDir.root}/build/info-test-process/statsTestTasks.json")
        val txt = File("${testProjectDir.root}/build/info-test-process/statsTestTasks.txt")
        assertTrue(json.exists())
        assertFalse(txt.exists())
        val body = json.readText()
        assertTrue(body.contains("\"summary\""))
        assertTrue(body.contains("\"byTask\""))
        assertTrue(body.contains("\"workers\""))
        assertTrue(body.contains("\"tags\""))
    }

    @Test
    fun develocityExtensionDslCanOptOutOfBuildScanReporting() {
        createProject(
            settingsExtra = """
                    infoTestProcess {
                        develocity {
                            enabled = false
                        }
                    }
            """.trimIndent()
        )

        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments("test", "--configuration-cache")
            .withPluginClasspath()
            .withGradleVersion("9.7.1")
            .build()

        assertTrue(File("${testProjectDir.root}/build/info-test-process/statsTestTasks.json").exists())
        assertFalse(File("${testProjectDir.root}/build/info-test-process/statsTestTasks.txt").exists())
    }

    private fun createProject(
        extraGradleProperties: String = "",
        settingsExtra: String = ""
    ) {
        testProjectDir.newFile("settings.gradle").appendText(
            """
                plugins {
                    id 'com.gradle.develocity' version '4.1'
                    id 'io.github.cdsap.testprocess'
                }
                develocity {
                    server = "https://example.invalid"
                    buildScan {
                        publishing.onlyIf { false }
                    }
                }
                $settingsExtra
            """.trimIndent()
        )
        testProjectDir.newFile("gradle.properties").appendText(
            """
                kotlin.internal.collectFUSMetrics=false
                $extraGradleProperties
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
