package io.github.cdsap.testprocess

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the security property from #128: never execute an unverified
 * gradle-wrapper.jar on CI. Accepts either the standalone wrapper-validation
 * action (v4+) or setup-gradle (v4+), which validates as a side effect.
 */
class CiWrapperValidationTest {
    private val workflowFile = File(".github/workflows/build.yaml")

    private val wrapperValidation =
        Regex("""uses:\s*gradle/actions/wrapper-validation@v([4-9]|\d{2,})""")
    private val setupGradle =
        Regex("""uses:\s*gradle/actions/setup-gradle@v([4-9]|\d{2,})""")

    @Test
    fun everyJobThatRunsGradlewValidatesTheWrapperFirst() {
        assertTrue("expected ${workflowFile.path}", workflowFile.isFile)
        val jobs = jobBodies(workflowFile.readText())
        assertTrue("build.yaml must define jobs", jobs.isNotEmpty())

        val jobsRunningGradlew = jobs.filter { (_, body) -> body.contains("./gradlew") }
        assertTrue(
            "expected at least one job that invokes ./gradlew",
            jobsRunningGradlew.isNotEmpty()
        )

        jobsRunningGradlew.forEach { (name, body) ->
            val validationMatch =
                wrapperValidation.find(body) ?: setupGradle.find(body)
            assertTrue(
                "job '$name' runs ./gradlew but does not use " +
                    "gradle/actions/wrapper-validation@v4+ or " +
                    "gradle/actions/setup-gradle@v4+",
                validationMatch != null
            )
            val firstGradlew = body.indexOf("./gradlew")
            assertTrue(
                "job '$name' must validate the wrapper before the first ./gradlew",
                validationMatch!!.range.first < firstGradlew
            )
        }
    }

    private fun jobBodies(yaml: String): Map<String, String> {
        val jobsMarker = Regex("""(?m)^jobs:\s*$""").find(yaml)
            ?: error("no top-level jobs: key in ${workflowFile.path}")
        val section = yaml.substring(jobsMarker.range.last + 1)
        val headers = Regex("""(?m)^ {4}([A-Za-z0-9_-]+):\s*$""").findAll(section).toList()
        require(headers.isNotEmpty()) { "no jobs found under jobs:" }

        return headers.mapIndexed { index, match ->
            val name = match.groupValues[1]
            val start = match.range.last + 1
            val end = headers.getOrNull(index + 1)?.range?.first ?: section.length
            name to section.substring(start, end)
        }.toMap()
    }
}
