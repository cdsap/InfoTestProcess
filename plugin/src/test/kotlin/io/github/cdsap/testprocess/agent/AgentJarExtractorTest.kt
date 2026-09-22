package io.github.cdsap.testprocess.agent

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream
import java.net.URLClassLoader

class AgentJarExtractorTest {
    @Rule
    @JvmField
    val tmp = TemporaryFolder()

    @Test
    fun preservesAgentResourcePath() {
        assert(AgentJarExtractor.AGENT_RESOURCE == "META-INF/agent/info-test-process-agent.jar")
    }

    @Test
    fun extractsAgentJarFromClassLoaderResource() {
        val payload = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x01)
        val classLoader = resourceClassLoader(AgentJarExtractor.AGENT_RESOURCE, payload)
        val target = File(tmp.root, "out/agent.jar")

        val extracted = AgentJarExtractor.extractAgentJar(target, classLoader)

        assert(extracted == target)
        assert(target.exists())
        assert(target.readBytes().contentEquals(payload))
    }

    @Test
    fun reusesExistingNonEmptyTarget() {
        val target = tmp.newFile("agent.jar")
        val existing = byteArrayOf(1, 2, 3, 4)
        target.writeBytes(existing)
        val classLoader = resourceClassLoader(AgentJarExtractor.AGENT_RESOURCE, byteArrayOf(9, 9, 9))

        val extracted = AgentJarExtractor.extractAgentJar(target, classLoader)

        assert(extracted == target)
        assert(target.readBytes().contentEquals(existing))
    }

    @Test
    fun returnsNullWhenResourceMissing() {
        val target = File(tmp.root, "missing/agent.jar")
        val classLoader = object : ClassLoader() {
            override fun getResourceAsStream(name: String): InputStream? = null
        }

        assert(AgentJarExtractor.extractAgentJar(target, classLoader) == null)
        assert(!target.exists())
    }

    private fun resourceClassLoader(resourcePath: String, content: ByteArray): ClassLoader {
        val root = tmp.newFolder("classpath")
        val resourceFile = File(root, resourcePath)
        resourceFile.parentFile.mkdirs()
        resourceFile.writeBytes(content)
        return URLClassLoader(arrayOf(root.toURI().toURL()), null)
    }
}
