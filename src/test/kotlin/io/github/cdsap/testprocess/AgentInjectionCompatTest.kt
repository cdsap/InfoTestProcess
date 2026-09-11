package io.github.cdsap.testprocess

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Regression for #115: the agent must not hard-fail consumer Test workers whose
 * JVM is older than the agent bytecode floor, and must still attach on supported JVMs.
 */
class AgentInjectionCompatTest {
    @Rule
    @JvmField
    val testProjectDir = TemporaryFolder()

    @Test
    fun java8TestWorkerSucceedsWithoutAgentInjection() {
        createProject(workerJava = 8)

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments("test", "--info")
            .withPluginClasspath()
            .withGradleVersion("9.7.1")
            .build()

        assertFalse(result.output.contains("UnsupportedClassVersionError"))
        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertFalse(
            "worker JVM below the agent floor must not receive -javaagent",
            result.output.contains("-javaagent:")
        )

        val registry = File(testProjectDir.root, "build/info-test-process/workers")
        val identityFiles = registry.listFiles()?.filter { it.name.endsWith(".json") && !it.name.endsWith(".stats.json") }
            .orEmpty()
        assertTrue(
            "Java 8 workers must not load the agent, so no identity snapshots are expected: $identityFiles",
            identityFiles.isEmpty()
        )
    }

    @Test
    fun java11TestWorkerStillReceivesAgent() {
        createProject(workerJava = 11)

        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments("test")
            .withPluginClasspath()
            .withGradleVersion("9.7.1")
            .build()

        val registry = File(testProjectDir.root, "build/info-test-process/workers")
        val identityFiles = registry.listFiles()?.filter { it.name.endsWith(".json") && !it.name.endsWith(".stats.json") }
            .orEmpty()
        assertTrue(
            "Java 11+ workers should still register via the agent: registry=$registry",
            identityFiles.isNotEmpty()
        )
    }

    private fun createProject(workerJava: Int) {
        testProjectDir.newFile("settings.gradle").writeText(
            """
                plugins {
                    id 'io.github.cdsap.testprocess'
                }
            """.trimIndent()
        )
        testProjectDir.newFile("build.gradle").writeText(
            """
                plugins {
                    id 'java'
                }
                repositories {
                    mavenCentral()
                }
                // Compile and run tests on the same JVM so the fixture isolates agent
                // injection (not an unrelated class-file mismatch from a higher release).
                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of($workerJava)
                    }
                }
                dependencies {
                    testImplementation 'junit:junit:4.13.2'
                }
                tasks.named('test', Test) {
                    // Force a fresh worker so registry side-effects are observable.
                    forkEvery = 1
                    javaLauncher = javaToolchains.launcherFor {
                        languageVersion = JavaLanguageVersion.of($workerJava)
                    }
                }
            """.trimIndent()
        )
        val testDir = File(testProjectDir.root, "src/test/java/com/example")
        testDir.mkdirs()
        File(testDir, "SmokeTest.java").writeText(
            """
                package com.example;

                import org.junit.Test;
                import static org.junit.Assert.assertEquals;

                public class SmokeTest {
                    @Test
                    public void adds() {
                        assertEquals(2, 1 + 1);
                    }
                }
            """.trimIndent()
        )
    }
}
