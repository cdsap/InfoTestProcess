package io.github.cdsap.testprocess

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Keeps dependency and plugin versions in gradle/libs.versions.toml (not inline in build scripts).
class VersionCatalogTest {
    private val catalog = File("../gradle/libs.versions.toml")
    private val buildScript = File("build.gradle.kts")

    @Test
    fun versionCatalogCentralizesPluginAndDependencyVersions() {
        assertTrue("gradle/libs.versions.toml must exist", catalog.isFile)

        val toml = catalog.readText()
        assertTrue("catalog must declare kotlin version", toml.contains("""kotlin = """))
        assertTrue("catalog must declare develocity version", toml.contains("""develocity = """))
        assertTrue("catalog must declare serialization version", toml.contains("""serialization = """))
        assertTrue("catalog must declare junit version", toml.contains("""junit = """))
        assertTrue(
            "catalog must declare develocity gradle plugin library",
            toml.contains("develocity-gradlePlugin") && toml.contains("com.gradle:develocity-gradle-plugin")
        )
        assertTrue(
            "catalog must declare kotlinx serialization library",
            toml.contains("kotlinx-serializationJson") &&
                toml.contains("org.jetbrains.kotlinx:kotlinx-serialization-json")
        )
        assertTrue(
            "catalog must declare junit library",
            toml.contains("""junit = { module = "junit:junit""")
        )
        assertTrue(
            "catalog must declare plugin-publish plugin",
            toml.contains("pluginPublish") && toml.contains("com.gradle.plugin-publish")
        )
        assertTrue(
            "catalog must declare kotlin serialization plugin",
            toml.contains("kotlin-serialization") &&
                toml.contains("org.jetbrains.kotlin.plugin.serialization")
        )
        assertTrue(
            "kotlin serialization plugin must share the kotlin version ref",
            toml.contains("version.ref = \"kotlin\"")
        )
        assertTrue(
            "develocity library must share the develocity version ref",
            toml.contains("version.ref = \"develocity\"")
        )
    }

    @Test
    fun pluginBuildScriptUsesVersionCatalogAliases() {
        assertTrue("plugin/build.gradle.kts must exist", buildScript.isFile)

        val script = buildScript.readText()
        assertTrue(
            "plugin build must apply plugin-publish via catalog alias",
            script.contains("alias(libs.plugins.pluginPublish)")
        )
        assertTrue(
            "plugin build must apply kotlin serialization via catalog alias",
            script.contains("alias(libs.plugins.kotlin.serialization)")
        )
        assertTrue(
            "plugin build must depend on develocity via catalog",
            script.contains("libs.develocity.gradlePlugin")
        )
        assertTrue(
            "plugin build must depend on kotlinx-serialization-json via catalog",
            script.contains("libs.kotlinx.serializationJson")
        )
        assertTrue(
            "plugin build must depend on junit via catalog",
            script.contains("libs.junit")
        )

        assertFalse(
            "plugin-publish version must not be hardcoded inline",
            Regex("""id\("com\.gradle\.plugin-publish"\)\s+version\s+"[^"]+"""").containsMatchIn(script)
        )
        assertFalse(
            "kotlin serialization plugin version must not be hardcoded inline",
            Regex("""kotlin\("plugin\.serialization"\)\s+version\s+"[^"]+"""").containsMatchIn(script)
        )
        assertFalse(
            "develocity dependency version must not be hardcoded inline",
            script.contains("com.gradle:develocity-gradle-plugin:")
        )
        assertFalse(
            "kotlinx-serialization-json version must not be hardcoded inline",
            script.contains("org.jetbrains.kotlinx:kotlinx-serialization-json:")
        )
        assertFalse(
            "junit version must not be hardcoded inline",
            script.contains("junit:junit:")
        )
    }
}
