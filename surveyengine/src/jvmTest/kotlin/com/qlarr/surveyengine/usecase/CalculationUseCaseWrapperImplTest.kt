package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.scriptengine.getCalculate
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals

class CalculationUseCaseWrapperImplTest {

    private val wrapper: CalculationUseCaseWrapper by lazy {
        CalculationUseCaseWrapper.create(ValidationUseCaseWrapper.create(design().toString()).validate())
    }

    private val ageTier =
        "!(Qage.value > 0) ? \"\" : Qage.value < 13 ? \"Child\" : Qage.value < 18 ? \"Teen\" : \"Adult\""

    @Test
    fun valid_calculations_have_no_errors() {
        val validated = validate(
            calculation("age_tier", ageTier),
            calculation("mode_and_disqualified", "Survey.mode + Survey.disqualified"),
            calculation("name_length", "Qname.value.length")
        )
        assertEquals(listOf("age_tier", "mode_and_disqualified", "name_length"), validated.map { it.code() })
        validated.forEach { assertEquals(JsonArray(emptyList()), it["errors"]) }
    }

    @Test
    fun validation_replaces_incoming_errors() {
        val stale = buildJsonObject {
            put("code", "age")
            put("expression", "Qage.value")
            putJsonArray("errors") { addJsonObject { put("name", "DuplicateCode") } }
        }
        assertEquals(JsonArray(emptyList()), validate(stale).single()["errors"])
    }

    @Test
    fun accessing_an_unknown_variable_is_a_script_error_with_offsets() {
        val error = validate(calculation("typo", "Qaeg.value + 1")).single()["errors"]!!.jsonArray.single().jsonObject
        assertEquals("ScriptError", error["name"]!!.jsonPrimitive.content)
        assertEquals(0, error["start"]!!.jsonPrimitive.int)
        assertEquals(10, error["end"]!!.jsonPrimitive.int)
    }

    @Test
    fun variables_that_are_not_always_saved_are_not_allowed() {
        val validated = validate(
            calculation("relevance", "Qage.relevance"),
            calculation("validity", "Qage.validity"),
            calculation("order", "Qage.order")
        )
        validated.forEach { assertEquals(listOf("ScriptError"), it["errors"]!!.names()) }
    }

    @Test
    fun invalid_and_duplicate_codes_are_reported_on_each_calculation() {
        val validated = validate(
            calculation("Age", "Qage.value"),
            calculation("dup", "Qage.value"),
            calculation("dup", "Qage.value + 1")
        )
        assertEquals(
            listOf(listOf("InvalidCode"), listOf("DuplicateCode"), listOf("DuplicateCode")),
            validated.map { it["errors"]!!.names() }
        )
    }

    @Test
    fun calculates_each_response_by_id() {
        val output = calculate(
            listOf(calculation("age_tier", ageTier)),
            "adult" to """{"Qage.value": 34}""",
            "teen" to """{"Qage.value": 15}""",
            "empty" to """{}"""
        )
        assertEquals(setOf("adult", "teen", "empty"), output.keys)
        assertEquals(JsonPrimitive("Adult"), output["adult"]!!.results["age_tier"])
        assertEquals(JsonPrimitive("Teen"), output["teen"]!!.results["age_tier"])
        assertEquals(JsonPrimitive(""), output["empty"]!!.results["age_tier"])
    }

    @Test
    fun a_variable_missing_from_the_response_is_undefined() {
        val output = calculate(listOf(calculation("missing", "Qage.value === undefined")), "r1" to "{}")
        assertEquals(JsonPrimitive(true), output["r1"]!!.results["missing"])
    }

    @Test
    fun null_and_undefined_results_are_left_out() {
        val output = calculate(
            listOf(calculation("null_result", "null"), calculation("undefined_result", "Qage.value")),
            "r1" to "{}"
        )["r1"]!!
        assertEquals(emptyMap(), output.results)
        assertEquals(emptyMap(), output.errors)
    }

    @Test
    fun a_throw_or_an_unserializable_result_is_an_error_without_affecting_others() {
        val output = calculate(
            listOf(
                calculation("throws", "Qname.value.toUpperCase()"),
                calculation("big_int", "1n"),
                calculation("age", "Qage.value")
            ),
            "r1" to """{"Qage.value": 34}"""
        )["r1"]!!
        assertEquals(mapOf("age" to JsonPrimitive(34)), output.results)
        assertEquals("TypeError", output.errors["throws"]!!.name)
        assertEquals("NotSerializable", output.errors["big_int"]!!.name)
    }

    @Test
    fun values_are_loaded_as_stored_without_type_checks() {
        val output = calculate(listOf(calculation("age", "Qage.value")), "r1" to """{"Qage.value": "not a number"}""")
        assertEquals(JsonPrimitive("not a number"), output["r1"]!!.results["age"])
    }

    @Test
    fun calculations_with_errors_are_not_executed() {
        val withError = buildJsonObject {
            put("code", "bad")
            put("expression", "Qage.value")
            putJsonArray("errors") { addJsonObject { put("name", "InvalidCode") } }
        }
        val output = calculate(
            listOf(withError, calculation("age", "Qage.value")),
            "r1" to """{"Qage.value": 34}"""
        )["r1"]!!
        assertEquals(setOf("age"), output.results.keys)
        assertEquals(emptyMap(), output.errors)
    }

    private fun validate(vararg calculations: JsonObject): List<JsonObject> =
        jsonMapper.parseToJsonElement(wrapper.validate(JsonArray(calculations.toList()).toString()))
            .jsonArray.map { it.jsonObject }

    private fun calculate(
        calculations: List<JsonObject>,
        vararg responses: Pair<String, String>
    ): Map<String, CalculationOutput> {
        val responsesJson = JsonObject(responses.associate { (id, values) -> id to jsonMapper.parseToJsonElement(values) })
        val output = wrapper.calculate(getCalculate(), JsonArray(calculations).toString(), responsesJson.toString())
        return jsonMapper.decodeFromString(MapSerializer(String.serializer(), CalculationOutput.serializer()), output)
    }

    private fun JsonObject.code() = this["code"]!!.jsonPrimitive.content

    private fun JsonElement.names() = jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }

    private fun calculation(code: String, expression: String) = buildJsonObject {
        put("code", code)
        put("expression", expression)
    }

    private fun question(code: String, returnType: String) = buildJsonObject {
        put("code", code)
        put("type", "text")
        put("instructionList", buildJsonArray {
            addJsonObject {
                put("code", "value")
                put("text", "")
                put("returnType", returnType)
                put("isActive", false)
            }
        })
    }

    private fun design() = buildJsonObject {
        put("code", "Survey")
        put("defaultLang", buildJsonObject {
            put("code", "en")
            put("name", "English")
        })
        put("navigationMode", "group_by_group")
        put("allowPrevious", true)
        put("skipInvalid", true)
        put("allowIncomplete", true)
        put("allowJump", true)
        put("groups", buildJsonArray {
            addJsonObject {
                put("code", "G1")
                put("groupType", "GROUP")
                put("questions", buildJsonArray {
                    add(question("Qage", "int"))
                    add(question("Qname", "string"))
                })
            }
            addJsonObject {
                put("code", "G2")
                put("groupType", "END")
                put("questions", JsonArray(emptyList()))
            }
        })
    }
}
