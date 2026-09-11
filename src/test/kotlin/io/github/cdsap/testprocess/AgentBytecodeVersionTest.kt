package io.github.cdsap.testprocess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.jar.JarFile

class AgentBytecodeVersionTest {
    @Test
    fun packagedAgentPremainClassTargetsJava11() {
        val agentUrl = javaClass.classLoader.getResource(
            "META-INF/agent/info-test-process-agent.jar"
        )
        assertNotNull("agent jar must be on the test classpath via processResources", agentUrl)

        val agentFile = java.io.File(agentUrl!!.toURI())
        assertTrue(agentFile.isFile)

        JarFile(agentFile).use { jar ->
            val entry = jar.getJarEntry(
                "io/github/cdsap/testprocess/agent/WorkerRegistrarAgent.class"
            )
            assertNotNull(entry)
            jar.getInputStream(entry).use { input ->
                val header = ByteArray(8)
                assertEquals(8, input.read(header))
                // big-endian u2 at offset 6
                val major = ((header[6].toInt() and 0xff) shl 8) or (header[7].toInt() and 0xff)
                assertEquals(
                    "agent must be Java 11 bytecode (major 55) so older test workers can load it",
                    55,
                    major
                )
            }
        }
    }
}
