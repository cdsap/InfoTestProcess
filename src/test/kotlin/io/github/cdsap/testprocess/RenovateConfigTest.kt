package io.github.cdsap.testprocess

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RenovateConfigTest {
    private val projectRoot = File(".").canonicalFile
    private val githubConfig = File(projectRoot, ".github/renovate.json")
    private val rootConfig = File(projectRoot, "renovate.json")

    @Test
    fun onlyGithubRenovateConfigExists() {
        assertTrue("expected ${githubConfig.path}", githubConfig.isFile)
        assertFalse(
            "root renovate.json must not compete with .github/renovate.json",
            rootConfig.exists()
        )
    }

    @Test
    fun packageRulesKeyAppearsOnceInRawConfig() {
        val raw = githubConfig.readText()
        val matches = Regex("\"packageRules\"").findAll(raw).count()
        assertEquals(
            "duplicate packageRules keys are silently discarded by JSON parsers",
            1,
            matches
        )
    }

    @Test
    fun renovateConfigUsesRecommendedPresetAndMavenAutomerge() {
        val config = Json.parseToJsonElement(githubConfig.readText()).jsonObject

        val extends = config.getValue("extends").jsonArray.map {
            it.jsonPrimitive.content
        }
        assertEquals(listOf("config:recommended"), extends)
        assertFalse("config:base is deprecated", "config:base" in extends)

        val rules = config.getValue("packageRules").jsonArray
        assertEquals(1, rules.size)

        val rule = rules[0].jsonObject
        assertEquals(
            listOf("maven"),
            rule.stringList("matchDatasources")
        )
        assertEquals(
            listOf(
                "https://dl.google.com/dl/android/maven2/",
                "https://plugins.gradle.org/m2/",
                "https://repo1.maven.org/maven2/"
            ),
            rule.stringList("registryUrls")
        )
        assertEquals(true, rule.getValue("automerge").jsonPrimitive.booleanOrNull)
        assertEquals("pr", rule.string("automergeType"))
        assertEquals("merge-commit", rule.string("automergeStrategy"))
    }

    private fun JsonObject.string(key: String): String =
        getValue(key).jsonPrimitive.content

    private fun JsonObject.stringList(key: String): List<String> =
        getValue(key).jsonArray.map { element ->
            require(element is JsonPrimitive) { "expected string in $key" }
            element.contentOrNull ?: error("expected string in $key")
        }
}
