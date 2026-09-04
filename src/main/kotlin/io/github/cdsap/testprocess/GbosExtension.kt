package io.github.cdsap.testprocess

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Settings-level configuration for InfoTestProcess.
 *
 * Prefer Gradle properties for CI opt-in; the DSL mirrors the same flags.
 */
abstract class InfoTestProcessExtension @Inject constructor(objects: ObjectFactory) {
    val gbos: GbosExtension = objects.newInstance(GbosExtension::class.java)

    fun gbos(action: Action<in GbosExtension>) {
        action.execute(gbos)
    }
}

/**
 * Opt-in GBOS configuration. All flags default to disabled.
 *
 * - [enabled] turns on both file sinks (JSON + NDJSON) unless overridden.
 * - [json] / [ndjson] refine file output independently of each other.
 * - [develocity] is independent of file output.
 */
abstract class GbosExtension {
    abstract val enabled: Property<Boolean>
    abstract val json: Property<Boolean>
    abstract val ndjson: Property<Boolean>
    abstract val develocity: Property<Boolean>
}
