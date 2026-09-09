package io.github.cdsap.testprocess

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class PublishedPomTest {
    @Test
    fun pluginMavenPomDoesNotDeclareDevelocityRuntimeDependency() {
        val pom = File("build/publications/pluginMaven/pom-default.xml")
        assert(pom.isFile) {
            "expected ${pom.path} — run generatePomFileForPluginMavenPublication before test"
        }
        val xml = pom.readText()
        assertFalse(
            "develocity-gradle-plugin must not appear in the published plugin POM",
            xml.contains("develocity-gradle-plugin")
        )
    }
}
