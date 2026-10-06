package io.github.cdsap.testprocess.fake

import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import com.gradle.develocity.agent.gradle.scan.BuildResult
import com.gradle.develocity.agent.gradle.scan.BuildScanConfiguration
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy
import java.util.Collections
import javax.inject.Inject

/**
 * Test-only stand-in for the Develocity settings plugin, resolved in TestKit builds under the
 * real plugin ID `com.gradle.develocity` (see `META-INF/gradle-plugins/com.gradle.develocity.properties`
 * in the test resources). It registers the `develocity` extension typed as the real
 * [DevelocityConfiguration], so both `withPlugin("com.gradle.develocity")` hooks and
 * `getByType(DevelocityConfiguration)` lookups see it.
 *
 * Build Scan calls are printed instead of published:
 * - `value(name, value)` -> `SCAN-VALUE <name>=<value>`
 * - `tag(tag)`           -> `SCAN-TAG <tag>`
 * - `buildFinished(action)` actions run at build end, so the values/tags they emit print too.
 *
 * Recorded buildFinished actions travel through the configuration cache on the
 * [FakeBuildFinishedHandoff] task (which finalizes every task), are handed to
 * [FakeBuildFinishedService] at execution time, and run when that service is closed at build
 * end — so they also replay when the configuration cache is reused.
 */
abstract class FakeDevelocityPlugin @Inject constructor(
    private val objects: ObjectFactory
) : Plugin<Settings> {

    override fun apply(settings: Settings) {
        val finishedActions = mutableListOf<Any>()
        val buildScan = FakeProxies.buildScan(objects, finishedActions)
        val develocity = FakeProxies.create(DevelocityConfiguration::class.java, objects) { method, args ->
            when (method.name) {
                "getBuildScan" -> buildScan
                "buildScan" -> {
                    @Suppress("UNCHECKED_CAST")
                    (args[0] as Action<Any>).execute(buildScan)
                    null
                }
                else -> FakeProxies.UNHANDLED
            }
        }
        settings.extensions.add(DevelocityConfiguration::class.java, "develocity", develocity)

        val service = settings.gradle.sharedServices.registerIfAbsent(
            SERVICE_NAME,
            FakeBuildFinishedService::class.java
        ) {}

        settings.gradle.projectsEvaluated {
            val root = rootProject
            val handoff = root.tasks.register(HANDOFF_TASK_NAME, FakeBuildFinishedHandoff::class.java) {
                usesService(service)
                this.service.set(service)
                this.finishedActions = finishedActions
            }
            allprojects {
                tasks.configureEach {
                    if (!(project == root && name == HANDOFF_TASK_NAME)) finalizedBy(handoff)
                }
            }
        }
    }

    private companion object {
        // Gradle stops build services in registration-name order; sorting last means the plugin's
        // `statsBuildService` has already written its state when buildFinished actions run.
        const val SERVICE_NAME = "zzzFakeDevelocityBuildFinished"
        const val HANDOFF_TASK_NAME = "fakeDevelocityBuildFinished"
    }
}

/**
 * Carries the recorded buildFinished actions through the configuration cache. Build service
 * parameters are isolated via Java serialization, which the plugin's (non-Serializable)
 * action lambdas do not support, whereas task fields are bean-serialized by the cache.
 */
abstract class FakeBuildFinishedHandoff : DefaultTask() {
    @get:Internal
    abstract val service: Property<FakeBuildFinishedService>

    @get:Internal
    var finishedActions: List<Any> = emptyList()

    @TaskAction
    fun handOff() {
        service.get().finishedActions.addAll(finishedActions)
    }
}

abstract class FakeBuildFinishedService : BuildService<BuildServiceParameters.None>, AutoCloseable {

    val finishedActions: MutableList<Any> = Collections.synchronizedList(mutableListOf())

    override fun close() {
        val result = FakeProxies.create(BuildResult::class.java, null) { method, _ ->
            if (method.name == "getFailures") emptyList<Throwable>() else FakeProxies.UNHANDLED
        }
        finishedActions.forEach {
            @Suppress("UNCHECKED_CAST")
            (it as Action<Any>).execute(result)
        }
    }
}

object FakeProxies {
    val UNHANDLED = Any()

    fun buildScan(objects: ObjectFactory?, finishedActions: MutableList<Any>): BuildScanConfiguration {
        lateinit var self: BuildScanConfiguration
        self = create(BuildScanConfiguration::class.java, objects) { method, args ->
            when (method.name) {
                "value" -> println("SCAN-VALUE ${args[0]}=${args[1]}")
                "tag" -> println("SCAN-TAG ${args[0]}")
                "buildFinished" -> finishedActions += args[0]!!
                "background" -> {
                    @Suppress("UNCHECKED_CAST")
                    (args[0] as Action<Any>).execute(self)
                }
                else -> return@create UNHANDLED
            }
            null
        }
        return self
    }

    fun <T> create(
        type: Class<T>,
        objects: ObjectFactory?,
        override: (Method, Array<Any?>) -> Any?
    ): T = type.cast(
        Proxy.newProxyInstance(
            FakeProxies::class.java.classLoader,
            arrayOf(type),
            DefaultsHandler(type, objects, override)
        )
    )

    /** Answers anything [override] leaves [UNHANDLED] with a sensible default. */
    private class DefaultsHandler(
        private val type: Class<*>,
        private val objects: ObjectFactory?,
        private val override: (Method, Array<Any?>) -> Any?
    ) : InvocationHandler {
        private val cache = mutableMapOf<String, Any?>()

        override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
            val arguments = args ?: emptyArray()
            if (method.declaringClass == Any::class.java) {
                return when (method.name) {
                    "equals" -> proxy === arguments[0]
                    "hashCode" -> System.identityHashCode(proxy)
                    else -> "Fake${type.simpleName}"
                }
            }
            val overridden = override(method, arguments)
            if (overridden !== UNHANDLED) return overridden

            if (arguments.size == 1 && arguments[0] is Action<*>) {
                // e.g. publishing(Action) configures the object returned by getPublishing().
                val getter = proxy.javaClass.methods.firstOrNull {
                    it.parameterCount == 0 &&
                        it.name == "get" + method.name.replaceFirstChar(Char::uppercaseChar)
                }
                if (getter != null) {
                    @Suppress("UNCHECKED_CAST")
                    (arguments[0] as Action<Any>).execute(getter.invoke(proxy))
                }
                return null
            }
            if (arguments.isNotEmpty()) return null
            return synchronized(cache) {
                cache.getOrPut(method.name) { defaultFor(method) }
            }
        }

        private fun defaultFor(method: Method): Any? {
            val returnType = method.returnType
            val elementTypes = (method.genericReturnType as? ParameterizedType)
                ?.actualTypeArguments
                ?.map { (it as? Class<*>) ?: Any::class.java }
                .orEmpty()
            return when {
                returnType == Void.TYPE -> null
                returnType == java.lang.Boolean.TYPE -> false
                returnType == Property::class.java ->
                    objects?.property(elementTypes.firstOrNull() ?: Any::class.java)
                returnType == ListProperty::class.java ->
                    objects?.listProperty(elementTypes.firstOrNull() ?: Any::class.java)
                returnType == SetProperty::class.java ->
                    objects?.setProperty(elementTypes.firstOrNull() ?: Any::class.java)
                returnType == MapProperty::class.java ->
                    objects?.mapProperty(
                        elementTypes.getOrNull(0) ?: Any::class.java,
                        elementTypes.getOrNull(1) ?: Any::class.java
                    )
                returnType.isInterface -> create(returnType, objects) { _, _ -> UNHANDLED }
                else -> null
            }
        }
    }
}
