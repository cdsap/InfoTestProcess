package io.github.cdsap.testprocess

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PublishedPomTest {
    private val pluginMavenPom = File("build/publications/pluginMaven/pom-default.xml")

    @Test
    fun pluginMavenPomDoesNotDeclareDevelocityRuntimeDependency() {
        val xml = readPluginMavenPom()
        assertFalse(
            "develocity-gradle-plugin must not appear in the published plugin POM",
            xml.contains("develocity-gradle-plugin")
        )
    }

    @Test
    fun pluginMavenPomDoesNotDeclareGbosSchemaTestDependencies() {
        val xml = readPluginMavenPom()
        assertFalse(
            "build-observability-schema is test-only and must not appear in the published plugin POM",
            xml.contains("build-observability-schema")
        )
        assertFalse(
            "json-schema-validator is test-only and must not appear in the published plugin POM",
            xml.contains("json-schema-validator")
        )
    }

    @Test
    fun pluginMavenPomCarriesProjectMetadata() {
        val xml = readPluginMavenPom()
        assertTrue("POM name", xml.contains("<name>InfoTestProcess</name>"))
        assertTrue(
            "POM description",
            xml.contains("Retrieve information of the Test process in your Build Scan or console")
        )
        assertTrue("POM license", xml.contains("The MIT License (MIT)"))
        assertTrue("POM license URL", xml.contains("https://opensource.org/licenses/MIT"))
        assertTrue("POM SCM URL", xml.contains("https://github.com/cdsap/InfoTestProcess/"))
        assertTrue(
            "POM SCM connection",
            xml.contains("scm:git:git://github.com/cdsap/InfoTestProcess/")
        )
        assertTrue("POM developer id", xml.contains("<id>cdsap</id>"))
        assertTrue("POM developer name", xml.contains("<name>Inaki Villar</name>"))
        assertTrue(
            "pluginMaven must publish as InfoTestProcess (marker target)",
            xml.contains("<artifactId>InfoTestProcess</artifactId>")
        )
        assertFalse(
            "subproject directory name must not become the published artifactId",
            xml.contains("<artifactId>plugin</artifactId>")
        )
        assertFalse(
            "orphaned testprocess artifactId must not be the consumed publication",
            xml.contains("<artifactId>testprocess</artifactId>")
        )
    }

    @Test
    fun orphanedTestProcessPublicationIsNotGenerated() {
        val orphanPom = File("build/publications/testProcessPublication/pom-default.xml")
        assertFalse(
            "testProcessPublication must not exist — metadata belongs on pluginMaven only",
            orphanPom.exists()
        )
    }

    private fun readPluginMavenPom(): String {
        assert(pluginMavenPom.isFile) {
            "expected ${pluginMavenPom.path} — run generatePomFileForPluginMavenPublication before test"
        }
        return pluginMavenPom.readText()
    }
}
