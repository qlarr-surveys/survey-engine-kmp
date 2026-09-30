package com.qlarr.surveyengine.validation

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.qlarr.surveyengine.model.ReservedCode
import com.qlarr.surveyengine.model.SurveyLang
import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.ext.copyComponentsToJson
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
            component.copyComponentsToJson(jsonObject).toString()
        )
    }

    @Test
    fun copies_instructions_and_errors2() {
        val component = Survey(
            instructionList = listOf(
                Instruction.Format(
                    "format_1",
                    text = "",
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
            "{\"code\":\"Survey\",\"qualifiedCode\":\"Survey\",\"instructionList\":[{\"code\":\"format_1\",\"contentPath\":[],\"lang\":\"en\",\"text\":\"\",\"errors\":[{\"name\":\"ScriptError\",\"message\":\"parse error\",\"start\":0,\"end\":10}]}]}",
            component.copyComponentsToJson(jsonObject).toString()
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
            component.copyComponentsToJson(jsonObject).toString()
        )
    }

    @Test
    fun keeps_json_object_other_values_intact() {
        val component = Survey(
            instructionList = listOf(SimpleState("", ReservedCode.Value)),
            errors = listOf(ComponentError.DUPLICATE_CODE)
        )
        val jsonObject = jsonMapper.parseToJsonElement("{\"code\":\"Survey\",\"foo\":\"bar\"}").jsonObject
        assertEquals("bar", component.copyComponentsToJson(jsonObject)["foo"].toString().replace("\"", ""))
    }

    @Test
    fun preserves_expanded_repeated_copies_and_bases_them_on_the_template_json() {
        val component = Survey(
            groups = listOf(
                Group(
                    "G1",
                    questions = listOf(
                        Question(
                            "Q1",
                            instructionList = listOf(SimpleState("Q1", ReservedCode.Value)),
                            repeatInfo = RepeatInfo.Repeatable(
                                range = listOf("1", "2"),
                                relevanceInstruction = "true"
                            )
                        ),
                        Question(
                            "Q1_1",
                            instructionList = listOf(SimpleState("Q1_1", ReservedCode.Value)),
                            repeatInfo = RepeatInfo.Repeated("1")
                        ),
                        Question(
                            "Q1_2",
                            instructionList = listOf(SimpleState("Q1_2", ReservedCode.Value)),
                            repeatInfo = RepeatInfo.Repeated("2")
                        ),
                        Question("Q2", instructionList = listOf(SimpleState("Q2", ReservedCode.Value)))
                    )
                )
            )
        )
        val jsonObject = jsonMapper.parseToJsonElement(
            "{\"code\":\"Survey\",\"groups\":[{\"code\":\"G1\",\"questions\":[" +
                "{\"code\":\"Q1\",\"content\":{\"en\":{\"label\":\"hi\"}}}," +
                "{\"code\":\"Q2\"}]}]}"
        ).jsonObject

        val questions = component.copyComponentsToJson(jsonObject)["groups"]!!.jsonArray[0]
            .jsonObject["questions"]!!.jsonArray

        assertEquals(
            listOf("Q1", "Q1_1", "Q1_2", "Q2"),
            questions.map { it.jsonObject["code"]!!.jsonPrimitive.content }
        )
        // the copy carries its own validated instructions and qualifiedCode
        assertEquals("Q1_1", questions[1].jsonObject["qualifiedCode"]!!.jsonPrimitive.content)
        assertEquals(
            "Q1_1",
            questions[1].jsonObject["instructionList"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        )
        // the copy inherits the template's authored content
        assertEquals(
            "hi",
            questions[1].jsonObject["content"]!!.jsonObject["en"]!!.jsonObject["label"]!!.jsonPrimitive.content
        )
        // the copy's repeatInfo is rewritten as a repeated block, not the template's repeatable
        val repeatInfo = questions[1].jsonObject["repeatInfo"]!!.jsonObject
        assertEquals("repeated", repeatInfo["type"]!!.jsonPrimitive.content)
        assertEquals("1", repeatInfo["token"]!!.jsonPrimitive.content)
    }

    @Test
    fun is_idempotent_purging_stale_repeated_copies_and_rebuilding_from_the_template() {
        val component = Survey(
            groups = listOf(
                Group(
                    "G1",
                    questions = listOf(
                        Question(
                            "Q1",
                            instructionList = listOf(SimpleState("Q1", ReservedCode.Value)),
                            repeatInfo = RepeatInfo.Repeatable(
                                range = listOf("1", "2"),
                                relevanceInstruction = "true"
                            )
                        ),
                        Question("Q1_1", repeatInfo = RepeatInfo.Repeated("1")),
                        Question("Q1_2", repeatInfo = RepeatInfo.Repeated("2"))
                    )
                )
            )
        )
        // Source JSON already carries stale copies from a previous round, including one (Q1_3) whose
        // token is no longer in range.
        val jsonObject = jsonMapper.parseToJsonElement(
            "{\"code\":\"Survey\",\"groups\":[{\"code\":\"G1\",\"questions\":[" +
                "{\"code\":\"Q1\",\"content\":{\"en\":{\"label\":\"hi\"}},\"repeatInfo\":{\"type\":\"repeatable\",\"range\":[\"1\",\"2\"],\"relevanceInstruction\":\"true\"}}," +
                "{\"code\":\"Q1_1\",\"repeatInfo\":{\"type\":\"repeated\",\"token\":\"1\"}}," +
                "{\"code\":\"Q1_2\",\"repeatInfo\":{\"type\":\"repeated\",\"token\":\"2\"}}," +
                "{\"code\":\"Q1_3\",\"repeatInfo\":{\"type\":\"repeated\",\"token\":\"3\"}}]}]}"
        ).jsonObject

        val questions = component.copyComponentsToJson(jsonObject)["groups"]!!.jsonArray[0]
            .jsonObject["questions"]!!.jsonArray

        // The stale Q1_3 is purged; copies are rebuilt from the template to exactly match the current range.
        assertEquals(
            listOf("Q1", "Q1_1", "Q1_2"),
            questions.map { it.jsonObject["code"]!!.jsonPrimitive.content }
        )
        // Rebuilt copies inherit the template's content, not whatever the stale copies held.
        assertEquals(
            "hi",
            questions[1].jsonObject["content"]!!.jsonObject["en"]!!.jsonObject["label"]!!.jsonPrimitive.content
        )
        assertEquals("repeated", questions[2].jsonObject["repeatInfo"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("2", questions[2].jsonObject["repeatInfo"]!!.jsonObject["token"]!!.jsonPrimitive.content)
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
            component.copyComponentsToJson(jsonObject).toString()
        )
    }

}