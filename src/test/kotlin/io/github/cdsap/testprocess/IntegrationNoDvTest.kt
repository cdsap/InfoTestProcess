package io.github.cdsap.testprocess

import junit.framework.TestCase.assertTrue
import org.gradle.kotlin.dsl.accessors.runtime.addDependencyTo
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class IntegrationNoDvTest {
    @Rule
    @JvmField
    val testProjectDir = TemporaryFolder()

    @Test
    fun testPluginIsCompatibleWithConfigurationCacheWithGradleEnterprise() {

        createProject()

        listOf("8.14.3", "9.1.0").forEach {
            val firstBuild = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments("test", "--configuration-cache")
                .withPluginClasspath()
                .withGradleVersion(it)
                .build()


            val secondBuild = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments("test", "--configuration-cache")
                .withPluginClasspath()
                .withGradleVersion(it)
                .build()
            assertTrue(firstBuild.output.contains("Configuration cache entry stored"))
            assertTrue(secondBuild.output.contains("Configuration cache entry reused."))
        }
    }

    @Test
    fun testPluginIsCompatibleWithProjectIsolation() {

        createProject()
        listOf("8.14.3", "9.1.0").forEach {
            val firstBuild = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments("test", "-Dorg.gradle.unsafe.isolated-projects=true")
                .withPluginClasspath()
                .withGradleVersion(it)
                .build()

            val secondBuild = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments("test", "-Dorg.gradle.unsafe.isolated-projects=true")
                .withPluginClasspath()
                .withGradleVersion(it)
                .build()
            assertTrue(firstBuild.output.contains("Configuration cache entry stored"))
            assertTrue(secondBuild.output.contains("Configuration cache entry reused."))
        }
    }

    @Test
    fun testFilesAreGenerated() {

        createProject()

        listOf("8.14.3", "9.1.0").forEach {
            val firstBuild = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments("test", "--configuration-cache")
                .withPluginClasspath()
                .withGradleVersion(it)
                .build()
            val firstJson = File("${testProjectDir.root}/statsTestTasks.json")
            assertTrue(firstJson.exists())
            assertTrue(firstJson.readText().contains("Test-Process"))

        }
    }

    private fun createProject() {
        testProjectDir.newFile("settings.gradle").appendText(
            """
                    plugins {
                       id 'io.github.cdsap.testprocess'
                    }

                """.trimIndent()
        )
        // disabling collect Fus metrics that fail with cc and KGP 2
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

        // 3) Write a sample Kotlin test class
        File("${testPkgDir.path}/SamplePluginTest.kt").writeText(
            """
        package com.example

        import kotlin.test.Test
        import kotlin.test.assertTrue

        class SamplePluginTest {
            @Test
            fun `smoke test runs`() {
                // Basic assertion so we know tests are executed
                assertTrue(1 + 1 == 2)
            }
        }
        """.trimIndent()
        )
        File("${testPkgDir.path}/AnotherTest.kt").writeText(
            """
        package com.example

        import kotlin.test.Test
        import kotlin.test.assertEquals

        class AnotherTest {
            @Test
            fun `another simple check`() {
                assertEquals("gradle".uppercase(), "GRADLE")
            }
        }
        """.trimIndent()
        )
    }
}
