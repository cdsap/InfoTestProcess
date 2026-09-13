package io.github.cdsap.testprocess

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

// Pin UTF-8 so compile fingerprints and text I/O match across CI OS legs and local JVMs.
class GradleFileEncodingTest {
    private val gradleProperties = File("../gradle.properties")

    @Test
    fun jvmArgsPinFileEncodingToUtf8() {
        assertTrue("gradle.properties must exist", gradleProperties.isFile)

        val props = Properties().apply {
            gradleProperties.inputStream().use { load(it) }
        }

        val jvmArgs = props.getProperty("org.gradle.jvmargs")
        assertTrue(
            "org.gradle.jvmargs must pin -Dfile.encoding=UTF-8 for portable builds",
            jvmArgs != null && jvmArgs.split(Regex("\\s+")).contains("-Dfile.encoding=UTF-8")
        )
    }
}
