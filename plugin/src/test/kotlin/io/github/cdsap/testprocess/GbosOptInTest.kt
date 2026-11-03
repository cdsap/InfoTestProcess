package io.github.cdsap.testprocess

import org.junit.Assert.assertEquals
import org.junit.Test

class GbosOptInTest {
    @Test
    fun exposesIndependentFileAndDevelocityOptInProperties() {
        assertEquals("infoTestProcess.gbos.enabled", GbosOptIn.PROPERTY_ENABLED)
        assertEquals(
            "infoTestProcess.gbos.develocity.enabled",
            GbosOptIn.PROPERTY_DEVELOCITY_ENABLED
        )
    }
}
