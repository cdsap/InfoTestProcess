package io.github.cdsap.testprocess

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

/**
 * Gradle property names and resolution rules for experimental GBOS opt-in.
 *
 * File sinks follow [PROPERTY_ENABLED] unless a sink-specific property is set.
 * Develocity publishing is detected from the applied Develocity plugin and is
 * intentionally not another GBOS opt-in switch.
 */
internal object GbosOptIn {
    const val PROPERTY_ENABLED = "infoTestProcess.gbos.enabled"
    const val PROPERTY_JSON = "infoTestProcess.gbos.json.enabled"
    const val PROPERTY_NDJSON = "infoTestProcess.gbos.ndjson.enabled"
    fun writeJson(enabled: Boolean, json: Boolean?): Boolean = json ?: enabled

    fun writeNdjson(enabled: Boolean, ndjson: Boolean?): Boolean = ndjson ?: enabled

    fun configureConventions(extension: GbosExtension, providers: ProviderFactory) {
        extension.enabled.convention(booleanProperty(providers, PROPERTY_ENABLED, default = false))
        // Absent sink-specific properties fall back to [enabled], so a single
        // infoTestProcess.gbos.enabled=true opts into both file sinks.
        extension.json.convention(
            optionalBooleanProperty(providers, PROPERTY_JSON).orElse(extension.enabled)
        )
        extension.ndjson.convention(
            optionalBooleanProperty(providers, PROPERTY_NDJSON).orElse(extension.enabled)
        )
    }

    private fun booleanProperty(
        providers: ProviderFactory,
        name: String,
        default: Boolean
    ): Provider<Boolean> =
        optionalBooleanProperty(providers, name).orElse(default)

    private fun optionalBooleanProperty(providers: ProviderFactory, name: String): Provider<Boolean> =
        providers.gradleProperty(name).map { it.toBoolean() }
}
