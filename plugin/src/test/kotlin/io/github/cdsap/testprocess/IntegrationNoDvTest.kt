package io.github.cdsap.testprocess

import junit.framework.TestCase.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class IntegrationNoDvTest {
    @Rule
    @JvmField
    val testProjectDir = TemporaryFolder()

    @Test
    fun testPluginIsCompatibleWithConfigurationCache() {
        // Proves Portal compatibility.features.configurationCache = true:
        // representative task twice with CC enabled, second run must be a HIT,
        // and --configuration-cache-problems=fail ensures no CC problems.
        createProject()

        val ccArgs = listOf("test", "--configuration-cache", "--configuration-cache-problems=fail")
        listOf("8.14.3", "9.1.0", "9.7.1").forEach {
            val firstBuild = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments(ccArgs)
                .withPluginClasspath()
                .withGradleVersion(it)
                .build()

            val secondBuild = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments(ccArgs)
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
        listOf("8.14.3", "9.1.0", "9.7.1").forEach {
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

        listOf("8.14.3", "9.1.0", "9.7.1").forEach {
            val firstBuild = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments("test", "--configuration-cache")
                .withPluginClasspath()
                .withGradleVersion(it)
                .build()
            val firstJson = File("${testProjectDir.root}/build/info-test-process/statsTestTasks.json")
            assertTrue(firstJson.exists())
            val body = firstJson.readText()
            assertTrue(body.contains("\"summary\""))
            assertTrue(body.contains("\"byTask\""))
            assertTrue(body.contains("\"workers\""))
            assertTrue(body.contains("\"tags\""))
            assertTrue(!File("${testProjectDir.root}/build/info-test-process/gbos.json").exists())
            assertTrue(!File("${testProjectDir.root}/build/info-test-process/gbos.ndjson").exists())

        }
    }

    @Test
    fun gbosFilesAreGeneratedOnlyWhenOptedIn() {

        createProject(
            extraGradleProperties = """
                    infoTestProcess.gbos.json.enabled=true
                    infoTestProcess.gbos.ndjson.enabled=true
            """.trimIndent()
        )

        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments("test", "--configuration-cache")
            .withPluginClasspath()
            .withGradleVersion("9.7.1")
            .build()

        val gbosJson = File("${testProjectDir.root}/build/info-test-process/gbos.json")
        val gbosNdjson = File("${testProjectDir.root}/build/info-test-process/gbos.ndjson")
        assertTrue(gbosJson.exists())
        assertTrue(gbosNdjson.exists())
        assertTrue(gbosJson.readText().contains("\"observations\""))
        val lines = gbosNdjson.readLines()
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.all { it.startsWith("{") && it.endsWith("}") })
    }

    @Test
    fun gbosMasterEnabledPropertyOptsIntoBothFileSinks() {
        createProject(
            extraGradleProperties = "infoTestProcess.gbos.enabled=true"
        )

        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments("test", "--configuration-cache")
            .withPluginClasspath()
            .withGradleVersion("9.7.1")
            .build()

        val gbosJson = File("${testProjectDir.root}/build/info-test-process/gbos.json")
        val gbosNdjson = File("${testProjectDir.root}/build/info-test-process/gbos.ndjson")
        assertTrue(gbosJson.exists())
        assertTrue(gbosNdjson.exists())
        assertTrue(gbosJson.readText().contains("\"observations\""))
    }

    @Test
    fun gbosExtensionDslCanEnableFileOutput() {
        createProject(
            settingsExtra = """
                    infoTestProcess {
                        gbos {
                            enabled = true
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

        assertTrue(File("${testProjectDir.root}/build/info-test-process/gbos.json").exists())
        assertTrue(File("${testProjectDir.root}/build/info-test-process/gbos.ndjson").exists())
    }

    private fun createProject(
        extraGradleProperties: String = "",
        settingsExtra: String = ""
    ) {
        testProjectDir.newFile("settings.gradle").appendText(
            """
                    plugins {
                       id 'io.github.cdsap.testprocess'
                    }
                    $settingsExtra

                """.trimIndent()
        )
        // disabling collect Fus metrics that fail with cc and KGP 2
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
