package io.github.cdsap.testprocess

import junit.framework.TestCase.assertTrue
import org.gradle.internal.impldep.org.junit.Assume.assumeTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InfoTestProcessPluginTest {
    @Rule
    @JvmField
    val testProjectDir = TemporaryFolder()

    @Test
    fun testPluginIsCompatibleWithConfigurationCacheWithGradleEnterprise() {
        assumeTrue(
            "Gradle Enterprise URL and Access Key are set",
            System.getenv("GE_URL") != null && System.getenv("GE_API_KEY") != null
        )

        testProjectDir.newFile("settings.gradle").appendText(
            """
                plugins {
                    id 'com.gradle.enterprise' version '3.12.2'
                }
                gradleEnterprise {
                    server = "${System.getenv("GE_URL")}"
                    accessKey="${System.getenv("GE_API_KEY")}"
                    buildScan {
                        capture { taskInputFiles = true }
                        publishAlways()
                    }
                }
            """.trimIndent()
        )
        testProjectDir.newFile("build.gradle").appendText(
            """
                plugins {
                    id 'org.jetbrains.kotlin.jvm' version '1.7.21'
                    id 'application'
                    id 'io.github.cdsap.testprocess'
                }
                repositories {
                    mavenCentral()
                }
            """.trimIndent()
        )
        listOf("7.5.1", "7.6").forEach {
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
}
