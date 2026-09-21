package io.github.cdsap.testprocess.report

import com.networknt.schema.Error
import com.networknt.schema.InputFormat
import com.networknt.schema.Schema
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Test/CI-only validator against the released
 * [build-observability-schema](https://github.com/cdsap/build-observability-schema)
 * Maven Central artifact. Schemas and registries are loaded from that artifact's
 * classpath (`schema/…`, `registry/…`); nothing is vendored under
 * `src/test/resources`. Not used at plugin runtime.
 */
internal class GbosContract private constructor(
    private val reportSchema: Schema,
    private val observationSchema: Schema,
    private val observationFragmentSchema: Schema,
    private val batchSchema: Schema,
    private val develocitySchema: Schema,
    private val indexNames: Set<String>
) {
    fun validateReport(report: JsonObject) {
        assertValid(reportSchema, report, "report")
        report["observations"]?.jsonArray?.forEachIndexed { index, item ->
            validateObservation(item.jsonObject, "observations[$index]")
        }
    }

    fun validateObservation(observation: JsonObject, where: String) {
        assertValid(observationSchema, observation, where)
    }

    fun validateObservationFragment(observation: JsonObject, where: String) {
        assertValid(observationFragmentSchema, observation, where)
    }

    fun validateBatch(batch: JsonObject) {
        // Batch schema $ref's observation.schema.json#/$defs/producer — exercises relative refs.
        assertValid(batchSchema, batch, "observation batch")
    }

    fun validateIndex(name: String, value: String) {
        assert(name in indexNames) { "index $name is not in the published develocity-indexes registry" }
        val number = value.toDoubleOrNull()
        assert(number != null && number.isFinite()) { "index $name must be finite numeric string" }
    }

    fun validateDevelocityProjection(projection: JsonObject) {
        assertValid(develocitySchema, projection, "develocity projection")
        projection["customValues"]!!.jsonArray.forEachIndexed { index, item ->
            val customValue = item.jsonObject
            val name = customValue["name"]!!.jsonPrimitive.content
            val value = customValue["value"]!!.jsonPrimitive.content
            when {
                name == "gbos.schema" -> assert(value == "1.0.0") { "unexpected GBOS schema version $value" }
                name == "gbos.version" -> assert(value == "0.0.3") { "unexpected GBOS contract version $value" }
                name == "gbos.producer" -> assert(value == "info-test-process") { "unexpected GBOS producer $value" }
                name == "gbos.v1.observations" -> validateBatch(
                    ReportJson.json.parseToJsonElement(value).jsonObject
                )
                name == "gbos.v1.observation" -> validateObservationFragment(
                    ReportJson.json.parseToJsonElement(value).jsonObject,
                    "customValues[$index].value"
                )
                name.startsWith("gbos.v1.index.") -> validateIndex(name, value)
                else -> error("unexpected GBOS custom value name $name")
            }
        }
    }

    private fun assertValid(schema: Schema, document: JsonObject, where: String) {
        assertValid(schema, ReportJson.json.encodeToString(JsonObject.serializer(), document), where)
    }

    companion object {
        private val SCHEMA_RESOURCES = listOf(
            "schema/report.schema.json",
            "schema/observation.schema.json",
            "schema/observation-batch.schema.json",
            "schema/observation-fragment.schema.json",
            "schema/develocity-projection.schema.json",
            "schema/develocity-indexes.schema.json",
            "schema/semantic-conventions.schema.json"
        )

        private val REGISTRY_RESOURCES = listOf(
            "registry/semantic-conventions.json",
            "registry/develocity-indexes.json"
        )

        fun load(): GbosContract {
            assert(javaClass.classLoader.getResource("gbos/schema/report.schema.json") == null) {
                "vendored gbos/schema resources must not be on the test classpath"
            }

            (SCHEMA_RESOURCES + REGISTRY_RESOURCES).forEach { path ->
                requireNotNull(javaClass.classLoader.getResource(path)) {
                    "Missing published contract resource $path — add io.github.cdsap:build-observability-schema as testImplementation"
                }
            }

            val schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            val reportSchema = schema(schemaRegistry, "schema/report.schema.json")
            val observationSchema = schema(schemaRegistry, "schema/observation.schema.json")
            val batchSchema = schema(schemaRegistry, "schema/observation-batch.schema.json")
            val observationFragmentSchema = schema(schemaRegistry, "schema/observation-fragment.schema.json")
            val develocitySchema = schema(schemaRegistry, "schema/develocity-projection.schema.json")
            val conventionsSchema = schema(schemaRegistry, "schema/semantic-conventions.schema.json")
            val indexesSchema = schema(schemaRegistry, "schema/develocity-indexes.schema.json")

            // Relative $ref resolution is exercised when report/batch schemas pull in observation.
            reportSchema.initializeValidators()
            observationSchema.initializeValidators()
            batchSchema.initializeValidators()
            observationFragmentSchema.initializeValidators()
            develocitySchema.initializeValidators()
            conventionsSchema.initializeValidators()
            indexesSchema.initializeValidators()

            val conventionsJson = resourceText("registry/semantic-conventions.json")
            val indexesJson = resourceText("registry/develocity-indexes.json")
            assertValid(conventionsSchema, conventionsJson, "registry/semantic-conventions.json")
            assertValid(indexesSchema, indexesJson, "registry/develocity-indexes.json")

            val indexes = ReportJson.json.parseToJsonElement(indexesJson).jsonObject
            val indexNames = indexes["indexes"]!!.jsonArray
                .map { it.jsonObject["name"]!!.jsonPrimitive.content }
                .toSet()

            return GbosContract(
                reportSchema = reportSchema,
                observationSchema = observationSchema,
                observationFragmentSchema = observationFragmentSchema,
                batchSchema = batchSchema,
                develocitySchema = develocitySchema,
                indexNames = indexNames
            )
        }

        private fun schema(registry: SchemaRegistry, path: String): Schema =
            registry.getSchema(SchemaLocation.of("classpath:$path"))

        private fun resourceText(path: String): String {
            val stream = javaClass.classLoader.getResourceAsStream(path)
                ?: error("Missing published contract resource $path")
            return stream.bufferedReader().use { it.readText() }
        }

        private fun assertValid(schema: Schema, document: String, where: String) {
            val errors: List<Error> = schema.validate(
                document,
                InputFormat.JSON
            ) { executionContext ->
                executionContext.executionConfig { config ->
                    config.formatAssertionsEnabled(true)
                }
            }
            assert(errors.isEmpty()) {
                "$where failed published schema validation:\n" +
                    errors.joinToString("\n") { " - ${it.message} (${it.instanceLocation})" }
            }
        }
    }
}
