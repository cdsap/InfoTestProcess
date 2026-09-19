package io.github.cdsap.testprocess

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GbosOptInTest {
    @Test
    fun defaultsDisableAllGbosOutputs() {
        assertFalse(GbosOptIn.writeJson(enabled = false, json = null))
        assertFalse(GbosOptIn.writeNdjson(enabled = false, ndjson = null))
        assertFalse(GbosOptIn.publishDevelocity(develocity = false))
    }

    @Test
    fun masterEnabledOptsIntoBothFileSinks() {
        assertTrue(GbosOptIn.writeJson(enabled = true, json = null))
        assertTrue(GbosOptIn.writeNdjson(enabled = true, ndjson = null))
    }

    @Test
    fun masterEnabledDoesNotImplyDevelocityPublishing() {
        assertFalse(GbosOptIn.publishDevelocity(develocity = false))
        assertTrue(GbosOptIn.publishDevelocity(develocity = true))
    }

    @Test
    fun sinkSpecificFlagsOverrideMasterEnabled() {
        assertFalse(GbosOptIn.writeJson(enabled = true, json = false))
        assertTrue(GbosOptIn.writeJson(enabled = false, json = true))
        assertFalse(GbosOptIn.writeNdjson(enabled = true, ndjson = false))
        assertTrue(GbosOptIn.writeNdjson(enabled = false, ndjson = true))
    }

    @Test
    fun develocityIsIndependentOfFileOptIn() {
        assertTrue(GbosOptIn.publishDevelocity(develocity = true))
        assertFalse(GbosOptIn.writeJson(enabled = false, json = null))
        assertFalse(GbosOptIn.writeNdjson(enabled = false, ndjson = null))
    }
}
