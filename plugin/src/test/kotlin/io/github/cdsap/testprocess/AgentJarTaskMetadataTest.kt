package io.github.cdsap.testprocess

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// agentJar without group/description is hidden from `./gradlew tasks` and unexplained
// under `./gradlew tasks --all` — newcomers need that task visible in the build report.
class AgentJarTaskMetadataTest {
    private val buildFile = File("build.gradle.kts")

    @Test
    fun agentJarDeclaresGroupAndDescription() {
        assertTrue("build.gradle.kts must exist", buildFile.isFile)

        val agentJarBlock = Regex(
            """val agentJar = tasks\.register<Jar>\("agentJar"\) \{([\s\S]*?)\n\}"""
        ).find(buildFile.readText())?.groupValues?.get(1)
            ?: error("agentJar task registration not found in build.gradle.kts")

        assertTrue(
            "agentJar must set group = \"build\" so it appears in ./gradlew tasks",
            agentJarBlock.contains("""group = "build"""")
        )
        assertTrue(
            "agentJar must describe the java agent jar for the task report",
            agentJarBlock.contains(
                "Builds the java agent jar that is bundled into the plugin and injected into consumer test workers."
            )
        )
    }
}
