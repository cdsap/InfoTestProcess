package io.github.cdsap.testprocess.agent

import java.io.File

object AgentJarExtractor {
    const val AGENT_RESOURCE = "META-INF/agent/info-test-process-agent.jar"

    fun extractAgentJar(target: File, classLoader: ClassLoader): File? {
        if (target.exists() && target.length() > 0) return target
        val resource = classLoader.getResourceAsStream(AGENT_RESOURCE) ?: return null
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        resource.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        if (!tmp.renameTo(target)) tmp.copyTo(target, overwrite = true).also { tmp.delete() }
        return target
    }
}
