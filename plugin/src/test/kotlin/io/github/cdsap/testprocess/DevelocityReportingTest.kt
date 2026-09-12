package io.github.cdsap.testprocess

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevelocityReportingTest {
    @Test
    fun reportsToDevelocityWhenPluginIsAppliedAndReportingIsEnabled() {
        assertTrue(DevelocityReporting.reportToDevelocity(develocityOnClasspath = true, enabled = true))
    }

    @Test
    fun optsOutToFileWhenReportingIsDisabledEvenWithDevelocityApplied() {
        assertFalse(DevelocityReporting.reportToDevelocity(develocityOnClasspath = true, enabled = false))
    }

    @Test
    fun reportsToFileWhenDevelocityIsNotApplied() {
        assertFalse(DevelocityReporting.reportToDevelocity(develocityOnClasspath = false, enabled = true))
        assertFalse(DevelocityReporting.reportToDevelocity(develocityOnClasspath = false, enabled = false))
    }
}
