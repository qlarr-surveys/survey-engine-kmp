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

    @Test
    fun validates_accessed_variables() {
        val validated = jsonMapper.parseToJsonElement(
            wrapper.validate(
                JsonArray(
                    listOf(calculation("ok", "Qage.value + 1"), calculation("typo", "Qaeg.value + 1"))
                ).toString()
            )
        ).jsonArray.map { it.jsonObject }
        assertEquals(0, validated[0]["errors"]!!.jsonArray.size)
        assertEquals(1, validated[1]["errors"]!!.jsonArray.size)
    }

    @Test
    fun calculates_each_response_by_id() {
        val output = jsonMapper.decodeFromString(
            MapSerializer(String.serializer(), CalculationOutput.serializer()),
            wrapper.calculate(
                getCalculate(),
                JsonArray(
                    listOf(
                        calculation("adult", "Qage.value >= 18"),
                        calculation("missing", "Qage.value === undefined"),
                        calculation("big_int", "1n"),
                        calculation("throws", "Qage.value.toFixed()")
                    )
                ).toString(),
                """{"r1": {"Qage.value": 34}, "r2": {}}"""
            )
        )
        assertEquals(JsonPrimitive(true), output["r1"]!!.results["adult"])
        assertEquals(JsonPrimitive(false), output["r1"]!!.results["missing"])
        assertEquals(JsonPrimitive(true), output["r2"]!!.results["missing"])
        assertEquals("NotSerializable", output["r1"]!!.errors["big_int"]!!.name)
        assertEquals("TypeError", output["r2"]!!.errors["throws"]!!.name)
        assertEquals(null, output["r1"]!!.errors["throws"])
    }

    private fun calculation(code: String, expression: String) = buildJsonObject {
        put("code", code)
        put("expression", expression)
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
                    addJsonObject {
                        put("code", "Qage")
                        put("type", "text")
                        put("instructionList", buildJsonArray {
                            addJsonObject {
                                put("code", "value")
                                put("text", "")
                                put("returnType", "int")
                                put("isActive", false)
                            }
                        })
                    }
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
