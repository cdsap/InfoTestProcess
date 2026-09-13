package io.github.cdsap.testprocess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

// Requires distributionSha256Sum so the wrapper verifies downloads (gradle.org/release-checksums).
class GradleWrapperChecksumTest {
    // Wrapper lives at the repo root; this test runs from the :plugin project dir.
    private val wrapperProperties = File("../gradle/wrapper/gradle-wrapper.properties")

    // Published Binary-only (-bin) ZIP checksum for gradle-9.7.1
    private val expectedGradle971BinSha256 =
        "acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a"

    @Test
    fun distributionSha256SumMatchesPublishedChecksumForConfiguredDistribution() {
        assertTrue("wrapper properties must exist", wrapperProperties.isFile)

        val props = Properties().apply {
            wrapperProperties.inputStream().use { load(it) }
        }

        val distributionUrl = props.getProperty("distributionUrl")
        assertTrue(
            "distributionUrl must point at gradle-9.7.1-bin.zip",
            distributionUrl != null && distributionUrl.contains("gradle-9.7.1-bin.zip")
        )

        val distributionSha256Sum = props.getProperty("distributionSha256Sum")
        assertTrue(
            "distributionSha256Sum must be set so the wrapper verifies the download",
            !distributionSha256Sum.isNullOrBlank()
        )
        assertTrue(
            "distributionSha256Sum must be a SHA-256 hex digest",
            distributionSha256Sum.matches(Regex("[a-fA-F0-9]{64}"))
        )
        assertEquals(
            "distributionSha256Sum must match the published gradle-9.7.1-bin.zip checksum",
            expectedGradle971BinSha256,
            distributionSha256Sum
        )
    }
}
