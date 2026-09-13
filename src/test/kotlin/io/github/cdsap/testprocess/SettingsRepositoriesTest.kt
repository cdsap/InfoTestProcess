package io.github.cdsap.testprocess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsRepositoriesTest {
    private val settings = File("settings.gradle.kts")

    @Test
    fun settingsDoesNotDeclareGoogleRepository() {
        val text = readSettings()
        assertFalse(
            "google() is unused and must not be declared in settings repositories",
            text.contains("google()")
        )
    }

    @Test
    fun settingsFiltersGradlePluginPortalWithExclusiveContent() {
        val text = readSettings()
        assertEquals(
            "pluginManagement and dependencyResolutionManagement each need exclusiveContent",
            2,
            Regex("""exclusiveContent""").findAll(text).count()
        )
        assertEquals(
            "both repository blocks must wrap gradlePluginPortal in exclusiveContent",
            2,
            Regex("""forRepository \{ gradlePluginPortal\(\) \}""").findAll(text).count()
        )
        assertTrue(
            "exclusiveContent must limit the portal to com.gradle.* groups",
            text.contains("""includeGroupByRegex("com\\.gradle.*")""")
        )
        assertTrue(
            "exclusiveContent must include org.gradle.* for portal-only Gradle org artifacts",
            text.contains("""includeGroupByRegex("org\\.gradle\\..*")""")
        )
        assertTrue(
            "mavenCentral must remain as the unfiltered default repository",
            text.contains("mavenCentral()")
        )
    }

    @Test
    fun settingsDoesNotDeclareUnfilteredGradlePluginPortal() {
        val text = readSettings()
        val portalCalls = Regex("""gradlePluginPortal\(\)""").findAll(text).count()
        val exclusivePortalCalls =
            Regex("""forRepository \{ gradlePluginPortal\(\) \}""").findAll(text).count()
        assertEquals(
            "every gradlePluginPortal() call must be inside exclusiveContent forRepository",
            portalCalls,
            exclusivePortalCalls
        )
        assertTrue("expected at least one filtered gradlePluginPortal()", portalCalls > 0)
    }

    private fun readSettings(): String {
        assertTrue("settings.gradle.kts must exist", settings.isFile)
        return settings.readText()
    }
}
