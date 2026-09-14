package io.github.cdsap.testprocess

import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PersistedDeserializationValueSourceTest {
    @Test
    fun fileInputUsesNonePathSensitivity() {
        val getter = PersistedDeserializationValueSource.Params::class.java.getMethod("getFile")

        assertNotNull(
            "getFile must be @InputFile",
            getter.getAnnotation(InputFile::class.java)
        )

        val pathSensitive = getter.getAnnotation(PathSensitive::class.java)
        assertNotNull(
            "getFile must be @PathSensitive so Gradle fingerprints content only",
            pathSensitive
        )
        assertEquals(
            "file inputs should use PathSensitivity.NONE — location under buildDirectory must not affect the fingerprint",
            PathSensitivity.NONE,
            pathSensitive!!.value
        )
    }
}
