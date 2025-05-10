package com.qlarr.surveyengine.validation

import kotlinx.serialization.json.jsonObject
import com.qlarr.surveyengine.model.ReservedCode
import com.qlarr.surveyengine.model.SurveyLang
import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.ext.copyErrorsToJSON
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.Instruction.SimpleState
import kotlin.test.assertEquals
import kotlin.test.Test

class CopyToJSONTest {

    @Test
    fun copies_instructions_and_errors() {
        val component = Survey(
            instructionList = listOf(SimpleState("", ReservedCode.Value)),
            errors = listOf(ComponentError.DUPLICATE_CODE)
        )
        val jsonObject = jsonMapper.parseToJsonElement("{\"code\":\"Survey\"}").jsonObject
        assertEquals(
            "{\"code\":\"Survey\",\"qualifiedCode\":\"Survey\",\"instructionList\":[{\"code\":\"value\",\"text\":\"\",\"returnType\":\"string\",\"isActive\":false}],\"errors\":[\"DUPLICATE_CODE\"]}",
            component.copyErrorsToJSON(jsonObject).toString()
        )
    }

    @Test
    fun copies_instructions_and_errors2() {
        val component = Survey(
            instructionList = listOf(
                Instruction.Reference(
                    "reference_1",
                    references = emptyList(),
                    lang = SurveyLang.EN.code,
                    errors = listOf(
                        InstructionError.ScriptError(
                           message = "parse error",
                            start = 0,
                            end = 10
                        )
                    )
                )
            )
        )
        val jsonObject = jsonMapper.parseToJsonElement("{\"code\":\"Survey\"}").jsonObject
        assertEquals(
            "{\"code\":\"Survey\",\"qualifiedCode\":\"Survey\",\"instructionList\":[{\"code\":\"reference_1\",\"references\":[],\"lang\":\"en\",\"errors\":[{\"name\":\"ScriptError\",\"message\":\"parse error\",\"start\":0,\"end\":10}]}]}",
            component.copyErrorsToJSON(jsonObject).toString()
        )
    }

    @Test
    fun overrides_json_existing_instructions_and_errors() {
        val component = Survey(
            instructionList = listOf(SimpleState("", ReservedCode.Value))
        )
        val jsonObject =
            jsonMapper.parseToJsonElement("{\"code\":\"Survey\",\"instructionList\":[{\"code\":\"conditional_relevance\",\"text\":\"false\",\"isActive\":false,\"returnType\":\"Boolean\"}]}").jsonObject
        assertEquals(
            "{\"code\":\"Survey\",\"instructionList\":[{\"code\":\"value\",\"text\":\"\",\"returnType\":\"string\",\"isActive\":false}],\"qualifiedCode\":\"Survey\"}",
            component.copyErrorsToJSON(jsonObject).toString()
        )
    }

    @Test
    fun keeps_json_object_other_values_intact() {
        val component = Survey(
            instructionList = listOf(SimpleState("", ReservedCode.Value)),
            errors = listOf(ComponentError.DUPLICATE_CODE)
        )
        val jsonObject = jsonMapper.parseToJsonElement("{\"code\":\"Survey\",\"foo\":\"bar\"}").jsonObject
        assertEquals("bar", component.copyErrorsToJSON(jsonObject)["foo"].toString().replace("\"", ""))
    }

    @Test
    fun copies_instructions_and_errors_to_nested_children_provided_same_code() {
        val component = Survey(
            groups = listOf(
                Group("G1", listOf(SimpleState("G1", ReservedCode.Value))),
                Group("G2", listOf(SimpleState("G2", ReservedCode.Value)))
            )
        )
        val jsonObject =
            jsonMapper.parseToJsonElement("{\"code\":\"Survey\",\"groups\":[{\"code\":\"G1\"},{\"code\":\"G2\"},{\"code\":\"G3\"}]}").jsonObject
        assertEquals(
            "{\"code\":\"Survey\",\"groups\":[{\"code\":\"G1\",\"qualifiedCode\":\"G1\",\"instructionList\":[{\"code\":\"value\",\"text\":\"G1\",\"returnType\":\"string\",\"isActive\":false}]},{\"code\":\"G2\",\"qualifiedCode\":\"G2\",\"instructionList\":[{\"code\":\"value\",\"text\":\"G2\",\"returnType\":\"string\",\"isActive\":false}]}],\"qualifiedCode\":\"Survey\"}",
            component.copyErrorsToJSON(jsonObject).toString()
        )
    }

}