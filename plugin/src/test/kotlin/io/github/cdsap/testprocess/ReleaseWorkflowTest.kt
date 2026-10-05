package io.github.cdsap.testprocess

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseWorkflowTest {
    private val workflowFile = File("../.github/workflows/release.yaml")

    @Test
    fun releaseWorkflowPublishesTheReleaseTagWithJdk17() {
        val workflow = workflowFile.readText()

        assertTrue("expected ${workflowFile.path}", workflowFile.isFile)
        assertTrue("workflow must listen for releases", workflow.contains("release:"))
        assertTrue("workflow must run for published releases", workflow.contains("types: [published]"))
        assertTrue(
            "workflow must check out the published release tag",
            workflow.contains("ref: \${{ github.event.release.tag_name }}")
        )
        assertTrue("workflow must grant read-only contents access", workflow.contains("permissions:\n    contents: read"))
        assertTrue("workflow must not grant contents write access", !workflow.contains("contents: write"))
        assertTrue("workflow must validate the Gradle wrapper", workflow.contains("gradle/actions/wrapper-validation@v6"))
        assertTrue("workflow must configure Gradle", workflow.contains("gradle/actions/setup-gradle@v6"))
        assertTrue("publication must use JDK 17", workflow.contains("java-version: 17"))
        assertTrue("publication must not use JDK 25", !workflow.contains("java-version: 25"))
        assertTrue(workflow.contains("GRADLE_PUBLISH_KEY: \${{ secrets.GRADLE_PUBLISH_KEY }}"))
        assertTrue(workflow.contains("GRADLE_PUBLISH_SECRET: \${{ secrets.GRADLE_PUBLISH_SECRET }}"))
        assertTrue(workflow.contains("publishPlugins --validate-only"))
        assertTrue(workflow.contains("./gradlew publishPlugins"))
        assertTrue(workflow.contains("github.event.release.draft"))
        assertTrue(workflow.contains("github.event.release.prerelease"))
        assertTrue(workflow.contains("GRADLE_PUBLISH_KEY"))
        assertTrue(workflow.contains("SNAPSHOT"))
        assertTrue(
            "publication validation must run before upload",
            workflow.indexOf("publishPlugins --validate-only") < workflow.indexOf("run: ./gradlew publishPlugins\n")
        )
    }
}
