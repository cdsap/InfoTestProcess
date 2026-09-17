package io.github.cdsap.testprocess

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the multi-project layout from issue #135: sources live under :plugin,
 * not the root project.
 */
class ProjectStructureTest {
    private val repoRoot = File("..").canonicalFile

    @Test
    fun settingsIncludesPluginSubproject() {
        val settings = File(repoRoot, "settings.gradle.kts")
        assertTrue("settings.gradle.kts must exist at repo root", settings.isFile)
        assertTrue(
            "settings must include the plugin subproject",
            settings.readText().contains("""include("plugin")""")
        )
    }

    @Test
    fun sourcesLiveUnderPluginNotRoot() {
        assertTrue(
            "plugin main sources must exist",
            File(repoRoot, "plugin/src/main/kotlin").isDirectory
        )
        assertTrue(
            "plugin test sources must exist",
            File(repoRoot, "plugin/src/test/kotlin").isDirectory
        )
        assertTrue(
            "plugin agent sources must exist",
            File(repoRoot, "plugin/src/agent/java").isDirectory
        )
        assertFalse(
            "root must not contain src/main after the #135 split",
            File(repoRoot, "src/main").exists()
        )
        assertFalse(
            "root must not contain src/test after the #135 split",
            File(repoRoot, "src/test").exists()
        )
        assertFalse(
            "root must not contain src/agent after the #135 split",
            File(repoRoot, "src/agent").exists()
        )
    }

    @Test
    fun rootBuildFileDoesNotApplyPluginPlugins() {
        val rootBuild = File(repoRoot, "build.gradle.kts")
        assertTrue("root build.gradle.kts must exist", rootBuild.isFile)
        val text = rootBuild.readText()
        assertFalse(
            "root must not apply java-gradle-plugin",
            text.contains("`java-gradle-plugin`") || text.contains("java-gradle-plugin")
        )
        assertFalse(
            "root must not apply kotlin-dsl",
            text.contains("`kotlin-dsl`") || text.contains("kotlin-dsl")
        )
        assertTrue(
            "plugin build script must apply java-gradle-plugin",
            File(repoRoot, "plugin/build.gradle.kts").readText().contains("`java-gradle-plugin`")
        )
    }
}
