package io.github.cdsap.testprocess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CompatibilityWorkflowTest {
    @Test
    fun compatibilityJobMatrixUsesExplicitIncludeCombinations() {
        val workflow = File(".github/workflows/build.yaml").readText()

        assertTrue(
            "compatibility job must exist in CI workflow",
            workflow.contains("\n    compatibility:")
        )

        val entries = parseMatrixInclude(workflow)
        assertEquals(
            listOf(
                MatrixEntry("e2e/consumer", "8.0.2", "17"),
                MatrixEntry("e2e/consumer", "8.14.3", "17"),
                MatrixEntry("e2e/consumer", "8.14.3", "21"),
                MatrixEntry("e2e/consumer", "9.7.1", "17"),
                MatrixEntry("e2e/consumer", "9.7.1", "21"),
                MatrixEntry("e2e/consumer", "9.7.1", "25"),
            ),
            entries
        )
    }

    private data class MatrixEntry(val project: String, val gradle: String, val java: String)

    private fun parseMatrixInclude(workflow: String): List<MatrixEntry> {
        val compatibilityBlock = workflow.substringAfter("\n    compatibility:")
        val includeBlock = compatibilityBlock.substringAfter("include:").substringBefore("runs-on:")
        val entryPattern = Regex(
            """- project: (\S+)\s+gradle: "([^"]+)"\s+java: "([^"]+)""""
        )
        return entryPattern.findAll(includeBlock).map { match ->
            MatrixEntry(
                project = match.groupValues[1],
                gradle = match.groupValues[2],
                java = match.groupValues[3]
            )
        }.toList()
    }
}
