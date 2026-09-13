package io.github.cdsap.testprocess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

// Requires org.gradle.caching=true so local and CI builds reuse task outputs.
class GradleBuildCacheTest {
    private val gradleProperties = File("gradle.properties")

    @Test
    fun buildCacheIsEnabled() {
        assertTrue("gradle.properties must exist", gradleProperties.isFile)

        val properties = Properties().apply {
            gradleProperties.inputStream().use { load(it) }
        }

        assertEquals(
            "org.gradle.caching must be true so the build cache is enabled",
            "true",
            properties.getProperty("org.gradle.caching")
        )
    }
}
