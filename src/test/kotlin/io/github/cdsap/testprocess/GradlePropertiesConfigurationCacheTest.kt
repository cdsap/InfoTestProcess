package io.github.cdsap.testprocess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

// The published plugin advertises configurationCache compatibility; the plugin's
// own build should enable the configuration cache by default (issue #131).
class GradlePropertiesConfigurationCacheTest {
    private val gradleProperties = File("gradle.properties")

    @Test
    fun configurationCacheIsEnabledInGradleProperties() {
        assertTrue("gradle.properties must exist", gradleProperties.isFile)

        val props = Properties().apply {
            gradleProperties.inputStream().use { load(it) }
        }

        assertEquals(
            "org.gradle.configuration-cache must be true so this build reuses configuration",
            "true",
            props.getProperty("org.gradle.configuration-cache")
        )
    }
}
