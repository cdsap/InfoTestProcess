package io.github.cdsap.testprocess

import junit.framework.TestCase.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assume.assumeTrue
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
                    id 'com.gradle.develocity' version '4.1'
                }
                develocity {
                    server = "${System.getenv("GE_URL")}"
                    accessKey="${System.getenv("GE_API_KEY")}"
                    buildScan {
                       publishing { true }
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
                    id 'io.github.cdsap.testprocess'
                }
                repositories {
                    mavenCentral()
                }
            """.trimIndent()
        )
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
}
