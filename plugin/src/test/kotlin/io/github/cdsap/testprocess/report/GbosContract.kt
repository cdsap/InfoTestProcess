package io.github.cdsap.testprocess.report

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Test/CI-only validator driven by the vendored
 * [cdsap/build-observability-schema](https://github.com/cdsap/build-observability-schema)
 * contracts under `src/test/resources/gbos`. Not used at plugin runtime.
 */
internal class GbosContract(
    private val reportRequired: Set<String>,
    private val observationRequired: Set<String>,
    private val reportProperties: Set<String>,
    private val observationProperties: Set<String>,
    private val develocityRequired: Set<String>,
    private val develocityProperties: Set<String>,
    private val observationNamePattern: Regex,
    private val indexNamePattern: Regex,
    private val tagPattern: Regex,
    private val metrics: Map<String, Metric>,
    private val attributes: Map<String, Attribute>,
    private val scopes: Set<String>,
    private val indexes: Map<String, Index>
) {
    fun validateReport(report: JsonObject) {
        assert(report.keys.containsAll(reportRequired)) { "report missing required fields" }
        assert(report.keys.all { it == "\$schema" || it in reportProperties }) {
            "report has unknown fields ${report.keys}"
        }
        assert(report["schemaVersion"]!!.jsonPrimitive.content == "1.0.0")
        validateLooseAttributes(report["resource"]!!.jsonObject, "resource")
        assert(report["observations"] is JsonArray)
        assert(report["observations"]!!.jsonArray.isNotEmpty())
        report["observations"]!!.jsonArray.forEachIndexed { index, item ->
            validateObservation(item.jsonObject, "observations[$index]")
        }
    }

    fun validateObservation(observation: JsonObject, where: String) {
        assert(observation.keys.containsAll(observationRequired)) { "$where missing required fields" }
        assert(observation.keys.all { it in observationProperties }) {
            "$where has unknown fields ${observation.keys}"
        }
        assert(observation["schemaVersion"]!!.jsonPrimitive.content == "1.0.0")
        assert(observation["producer"]!!.jsonObject["name"]!!.jsonPrimitive.content == "info-test-process")
        assert(observation["scope"]!!.jsonPrimitive.content in scopes) { "$where scope" }
        assert(
            observation["aggregationScope"]!!.jsonPrimitive.content in
                setOf("entity", "task", "project", "build")
        ) { "$where aggregationScope" }
        validateRegisteredAttributes(observation["attributes"]!!.jsonObject, "$where.attributes")

        val measurements = observation["measurements"]!!.jsonArray
        assert(measurements.isNotEmpty()) { "$where measurements empty" }
        val seen = mutableSetOf<Pair<String, String>>()
        measurements.forEachIndexed { index, item ->
            val measurement = item.jsonObject
            val name = measurement["name"]!!.jsonPrimitive.content
            val metric = metrics.getValue(name)
            val aggregation = measurement["aggregation"]!!.jsonPrimitive.content
            assert(measurement.keys == setOf("name", "value", "unit", "aggregation")) {
                "$where.measurements[$index] fields"
            }
            assert(measurement["unit"]!!.jsonPrimitive.content == metric.unit) {
                "$where.measurements[$index] unit"
            }
            assert(aggregation in metric.allowedAggregations) {
                "$where.measurements[$index] aggregation"
            }
            assert(measurement["value"]!!.jsonPrimitive.doubleOrNull != null) {
                "$where.measurements[$index] value"
            }
            assert(seen.add(name to aggregation)) {
                "$where duplicate measurement $name/$aggregation"
            }
        }

        observation["diagnostics"]?.jsonArray?.forEachIndexed { index, item ->
            val diagnostic = item.jsonObject
            assert(diagnostic.keys.containsAll(setOf("code", "severity"))) {
                "$where.diagnostics[$index] fields"
            }
            assert(diagnostic["severity"]!!.jsonPrimitive.content in setOf("info", "warning", "error"))
        }
    }

    fun validateIndex(name: String, value: String) {
        assert(indexNamePattern.matches(name)) { "invalid index name $name" }
        val index = indexes.getValue(name)
        val number = value.toDoubleOrNull()
        assert(number != null && number.isFinite()) { "index $name must be finite numeric string" }
        assert(index.producer == "info-test-process")
        assert(index.scope in scopes)
        assert(index.metric in metrics)
        assert(index.aggregation in metrics.getValue(index.metric).allowedAggregations)
        assert(index.aggregationScope == "build")
    }

    fun validateDevelocityProjection(projection: JsonObject) {
        assert(projection.keys.containsAll(develocityRequired)) {
            "develocity projection missing required fields"
        }
        assert(projection.keys.all { it == "\$schema" || it in develocityProperties }) {
            "develocity projection has unknown fields ${projection.keys}"
        }
        assert(projection["schemaVersion"]!!.jsonPrimitive.content == "1.0.0")

        val customValues = projection["customValues"]!!.jsonArray
        customValues.forEachIndexed { index, item ->
            val customValue = item.jsonObject
            assert(customValue.keys == setOf("name", "value")) { "customValues[$index] fields" }
            val name = customValue["name"]!!.jsonPrimitive.content
            val value = customValue["value"]!!.jsonPrimitive.content
            assert(value.length <= 90_000) { "customValues[$index] value too long" }
            when {
                name == "gbos.v1.observation" -> {
                    assert(observationNamePattern.matches(name))
                    validateObservation(
                        ReportJson.json.parseToJsonElement(value).jsonObject,
                        "customValues[$index].value"
                    )
                }
                name.startsWith("gbos.v1.index.") -> validateIndex(name, value)
                else -> error("unexpected GBOS custom value name $name")
            }
        }

        projection["tags"]!!.jsonArray.forEachIndexed { index, item ->
            assert(tagPattern.matches(item.jsonPrimitive.content)) {
                "tags[$index] ${item.jsonPrimitive.content}"
            }
        }
    }

    private fun validateRegisteredAttributes(actual: JsonObject, where: String) {
        actual.forEach { (name, value) ->
            val attribute = attributes.getValue(name)
            when (attribute.type) {
                "string" -> assert(value.jsonPrimitive.contentOrNull != null) {
                    "$where.$name must be string"
                }
                "integer" -> assert(value.jsonPrimitive.intOrNull != null) {
                    "$where.$name must be integer"
                }
                "number" -> assert(value.jsonPrimitive.doubleOrNull != null) {
                    "$where.$name must be number"
                }
                "boolean" -> assert(value.jsonPrimitive.booleanOrNull != null) {
                    "$where.$name must be boolean"
                }
                else -> error("unsupported attribute type ${attribute.type}")
            }
            if (attribute.values.isNotEmpty()) {
                assert(value.jsonPrimitive.content in attribute.values) {
                    "$where.$name has invalid value"
                }
            }
        }
    }

    private fun validateLooseAttributes(actual: JsonObject, where: String) {
        actual.forEach { (_, value) ->
            val primitive = value as? JsonPrimitive
                ?: error("$where values must be primitives")
            assert(
                primitive.contentOrNull != null ||
                    primitive.doubleOrNull != null ||
                    primitive.booleanOrNull != null
            ) { "$where values must be string/number/boolean" }
        }
    }

    companion object {
        private val DEFAULT_OBSERVATION_NAME = Regex("^gbos\\.v1\\.observation$")
        private val DEFAULT_INDEX_NAME = Regex(
            "^gbos\\.v1\\.index\\.[a-z0-9_]+\\.[a-z][a-z0-9_.]*\\.(last|min|max|sum|count)$"
        )
        private val DEFAULT_TAG = Regex("^gbos:v1:[a-z0-9][a-z0-9:-]*$")

        fun load(): GbosContract {
            val reportSchema = resourceJson("gbos/schema/report.schema.json")
            val observationSchema = resourceJson("gbos/schema/observation.schema.json")
            val develocitySchema = resourceJson("gbos/schema/develocity-projection.schema.json")
            val conventions = resourceJson("gbos/registry/semantic-conventions.json")
            val develocityIndexes = resourceJson("gbos/registry/develocity-indexes.json")
            return GbosContract(
                reportRequired = required(reportSchema),
                observationRequired = required(observationSchema),
                reportProperties = properties(reportSchema),
                observationProperties = properties(observationSchema),
                develocityRequired = required(develocitySchema),
                develocityProperties = properties(develocitySchema),
                observationNamePattern = DEFAULT_OBSERVATION_NAME,
                indexNamePattern = DEFAULT_INDEX_NAME,
                tagPattern = DEFAULT_TAG,
                metrics = conventions["metrics"]!!.jsonArray.associate {
                    val metric = it.jsonObject
                    metric["name"]!!.jsonPrimitive.content to Metric(
                        unit = metric["unit"]!!.jsonPrimitive.content,
                        allowedAggregations = metric["allowedAggregations"]!!.jsonArray
                            .map { aggregation -> aggregation.jsonPrimitive.content }
                            .toSet()
                    )
                },
                attributes = conventions["attributes"]!!.jsonArray.associate {
                    val attribute = it.jsonObject
                    attribute["name"]!!.jsonPrimitive.content to Attribute(
                        type = attribute["type"]!!.jsonPrimitive.content,
                        values = attribute["values"]?.jsonArray
                            ?.map { value -> value.jsonPrimitive.content }
                            ?.toSet()
                            ?: emptySet()
                    )
                },
                scopes = conventions["scopes"]!!.jsonArray
                    .map { it.jsonObject["name"]!!.jsonPrimitive.content }
                    .toSet(),
                indexes = develocityIndexes["indexes"]!!.jsonArray.associate {
                    val index = it.jsonObject
                    index["name"]!!.jsonPrimitive.content to Index(
                        producer = index["producer"]!!.jsonPrimitive.content,
                        scope = index["scope"]!!.jsonPrimitive.content,
                        metric = index["metric"]!!.jsonPrimitive.content,
                        aggregation = index["aggregation"]!!.jsonPrimitive.content,
                        aggregationScope = index["aggregationScope"]!!.jsonPrimitive.content
                    )
                }
            )
        }

        private fun resourceJson(path: String): JsonObject {
            val stream = javaClass.classLoader.getResourceAsStream(path)
                ?: error("Missing test resource $path")
            return ReportJson.json.parseToJsonElement(stream.bufferedReader().readText()).jsonObject
        }

        private fun required(schema: JsonObject): Set<String> =
            schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()

        private fun properties(schema: JsonObject): Set<String> =
            schema["properties"]!!.jsonObject.keys
    }

    internal data class Metric(
        val unit: String,
        val allowedAggregations: Set<String>
    )

    internal data class Attribute(
        val type: String,
        val values: Set<String>
    )

    internal data class Index(
        val producer: String,
        val scope: String,
        val metric: String,
        val aggregation: String,
        val aggregationScope: String
    )
}
