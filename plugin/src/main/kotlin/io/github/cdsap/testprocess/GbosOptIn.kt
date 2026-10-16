package io.github.cdsap.testprocess

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

/**
 * Gradle property names and resolution rules for experimental GBOS opt-in.
 *
 * A single [PROPERTY_ENABLED] flag drives both local file sinks (JSON + NDJSON).
 * Develocity publishing is detected from the applied Develocity plugin and is
 * intentionally not another GBOS opt-in switch.
 */
internal object GbosOptIn {
    const val PROPERTY_ENABLED = "infoTestProcess.gbos.enabled"

    fun configureConventions(extension: GbosExtension, providers: ProviderFactory) {
        extension.enabled.convention(booleanProperty(providers, PROPERTY_ENABLED, default = false))
    }

    private fun booleanProperty(
        providers: ProviderFactory,
        name: String,
        default: Boolean
    ): Provider<Boolean> =
        providers.gradleProperty(name).map { it.toBoolean() }.orElse(default)
}
