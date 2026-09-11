package io.github.cdsap.testprocess

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the PR/push CI contract from #121: full `build`, Gradle action caching,
 * and a proportionate timeout on the unit-test job.
 */
class CiWorkflowContractTest {
    private val workflow = File(".github/workflows/build.yaml").readText()

    @Test
    fun prBranchRunsGradleBuildNotOnlyTest() {
        assertTrue(
            "prBranch must run ./gradlew build so publication/POM wiring is validated",
            workflow.contains("run: ./gradlew build")
        )
        assertFalse(
            "prBranch must not stop at ./gradlew test",
            Regex("""name:\s*Execute Gradle build\s*\n\s*run:\s*\./gradlew test""")
                .containsMatchIn(workflow)
        )
    }

    @Test
    fun allJobsUseSetupGradleWithMainWritableCache() {
        val setupGradleBlocks = Regex(
            """uses:\s*gradle/actions/setup-gradle@v4\s*\n\s*with:\s*\n\s*cache-read-only:\s*\$\{\{\s*github\.ref\s*!=\s*'refs/heads/main'\s*\}\}"""
        ).findAll(workflow).count()
        assertTrue(
            "expected setup-gradle@v4 with cache-read-only on every job (found $setupGradleBlocks)",
            setupGradleBlocks >= 3
        )
    }

    @Test
    fun prBranchTimeoutIsProportionate() {
        val prBranch = workflow.substringAfter("prBranch:").substringBefore("e2e-cc:")
        val timeout = Regex("""timeout-minutes:\s*(\d+)""").find(prBranch)?.groupValues?.get(1)?.toInt()
        assertTrue("prBranch must declare timeout-minutes", timeout != null)
        assertTrue(
            "prBranch timeout should be 15–20 minutes, was $timeout",
            timeout in 15..20
        )
    }
}
