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
    val develocity: DevelocityReportingExtension =
        objects.newInstance(DevelocityReportingExtension::class.java)
    val gbos: GbosExtension = objects.newInstance(GbosExtension::class.java)

    fun develocity(action: Action<in DevelocityReportingExtension>) {
        action.execute(develocity)
    }

    fun gbos(action: Action<in GbosExtension>) {
        action.execute(gbos)
    }
}

/**
 * Legacy Build Scan reporting. When the Develocity plugin is applied, reporting is
 * enabled by default; set [DevelocityReportingExtension.enabled] to false to write
 * `statsTestTasks.json` instead.
 */
abstract class DevelocityReportingExtension {
    abstract val enabled: Property<Boolean>
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
