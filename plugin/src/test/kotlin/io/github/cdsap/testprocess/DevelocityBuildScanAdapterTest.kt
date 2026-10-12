package io.github.cdsap.testprocess

import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import org.gradle.api.provider.Provider
import org.junit.Assert.assertEquals
import org.junit.Test

class DevelocityBuildScanAdapterTest {
    @Test
    fun registerConsumesReportReadyData() {
        val register = DevelocityBuildScanAdapter::class.java.getDeclaredMethod(
            "register",
            DevelocityConfiguration::class.java,
            Provider::class.java
        )

        assertEquals(
            "org.gradle.api.provider.Provider<io.github.cdsap.testprocess.report.ReportDocument>",
            register.genericParameterTypes[1].typeName
        )
    }
}
