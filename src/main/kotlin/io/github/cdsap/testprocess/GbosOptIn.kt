package io.github.cdsap.testprocess

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

/**
 * Gradle property names and resolution rules for experimental GBOS opt-in.
 *
 * File sinks follow [PROPERTY_ENABLED] unless a sink-specific property is set.
 * Develocity publishing is always separate from file output.
 */
internal object GbosOptIn {
    const val PROPERTY_ENABLED = "infoTestProcess.gbos.enabled"
    const val PROPERTY_JSON = "infoTestProcess.gbos.json.enabled"
    const val PROPERTY_NDJSON = "infoTestProcess.gbos.ndjson.enabled"
    const val PROPERTY_DEVELOCITY = "infoTestProcess.gbos.develocity.enabled"

    fun writeJson(enabled: Boolean, json: Boolean?): Boolean = json ?: enabled

    fun writeNdjson(enabled: Boolean, ndjson: Boolean?): Boolean = ndjson ?: enabled

    fun publishDevelocity(develocity: Boolean): Boolean = develocity

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
        extension.develocity.convention(
            booleanProperty(providers, PROPERTY_DEVELOCITY, default = false)
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
