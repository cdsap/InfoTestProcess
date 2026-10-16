package io.github.cdsap.testprocess

import org.junit.Assert.assertEquals
import org.junit.Test

class GbosOptInTest {
    @Test
    fun exposesSingleFileSinkOptInProperty() {
        assertEquals("infoTestProcess.gbos.enabled", GbosOptIn.PROPERTY_ENABLED)
    }
}
