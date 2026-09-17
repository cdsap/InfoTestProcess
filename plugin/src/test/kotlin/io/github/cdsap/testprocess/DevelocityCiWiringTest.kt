package io.github.cdsap.testprocess

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the contract that CI-injected GE_URL / GE_API_KEY are consumed by this
 * project's own Develocity configuration (issue #122), not left as dead env wiring.
 */
class DevelocityCiWiringTest {
    @Test
    fun settingsAppliesDevelocityAndReadsCiCredentials() {
        val settings = File("../settings.gradle.kts").readText()
        assertTrue(
            "settings.gradle.kts must apply com.gradle.develocity so CI can publish Build Scans",
            settings.contains("""id("com.gradle.develocity")""")
        )
        assertTrue(
            "settings.gradle.kts must read GE_URL for the Develocity server",
            settings.contains("GE_URL")
        )
        assertTrue(
            "settings.gradle.kts must map GE_API_KEY to accessKey (otherwise CI secrets stay unused)",
            settings.contains("GE_API_KEY") && settings.contains("accessKey")
        )
        assertTrue(
            "Build Scan publishing must be gated on authentication",
            settings.contains("publishing.onlyIf") && settings.contains("isAuthenticated")
        )
    }

    @Test
    fun workflowInjectsGeCredentialsIntoProjectBuild() {
        val workflow = File("../.github/workflows/build.yaml").readText()
        assertTrue(
            "CI must still inject GE_URL for the project Gradle build",
            workflow.contains("GE_URL: \${{ secrets.GE_URL }}")
        )
        assertTrue(
            "CI must still inject GE_API_KEY for the project Gradle build",
            workflow.contains("GE_API_KEY: \${{ secrets.GE_API_KEY }}")
        )
    }
}
