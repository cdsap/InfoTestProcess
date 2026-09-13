package io.github.cdsap.testprocess

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

/**
 * Gradle property names and resolution rules for legacy Develocity reporting.
 *
 * When the Develocity plugin is applied, reporting to the Build Scan is enabled by
 * default. Set [PROPERTY_ENABLED]=false to opt out and write [statsTestTasks.json]
 * instead.
 */
internal object DevelocityReporting {
    const val PROPERTY_ENABLED = "infoTestProcess.develocity.enabled"

    fun reportToDevelocity(develocityOnClasspath: Boolean, enabled: Boolean): Boolean =
        develocityOnClasspath && enabled

    fun configureConventions(
        extension: DevelocityReportingExtension,
        providers: ProviderFactory,
        develocityOnClasspath: Boolean
    ) {
        extension.enabled.convention(
            optionalBooleanProperty(providers, PROPERTY_ENABLED).orElse(develocityOnClasspath)
        )
    }

    private fun optionalBooleanProperty(providers: ProviderFactory, name: String): Provider<Boolean> =
        providers.gradleProperty(name).map { it.toBoolean() }
}
