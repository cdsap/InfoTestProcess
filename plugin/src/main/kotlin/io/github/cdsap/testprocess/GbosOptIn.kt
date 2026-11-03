package io.github.cdsap.testprocess

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

/**
 * Gradle property names and resolution rules for experimental GBOS opt-in.
 *
 * File sinks and the Develocity projection have independent opt-ins. Gradle
 * properties provide conventions; explicit DSL values take precedence.
 */
internal object GbosOptIn {
    const val PROPERTY_ENABLED = "infoTestProcess.gbos.enabled"
    const val PROPERTY_DEVELOCITY_ENABLED = "infoTestProcess.gbos.develocity.enabled"

    fun configureConventions(extension: GbosExtension, providers: ProviderFactory) {
        extension.enabled.convention(booleanProperty(providers, PROPERTY_ENABLED, default = false))
        extension.develocity.enabled.convention(
            booleanProperty(providers, PROPERTY_DEVELOCITY_ENABLED, default = false)
        )
    }

    private fun booleanProperty(
        providers: ProviderFactory,
        name: String,
        default: Boolean
    ): Provider<Boolean> =
        providers.gradleProperty(name).map { it.toBoolean() }.orElse(default)
}
