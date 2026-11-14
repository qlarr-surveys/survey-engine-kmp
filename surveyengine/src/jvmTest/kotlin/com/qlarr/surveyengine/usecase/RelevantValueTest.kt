package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.model.NavigationJsonOutput
import com.qlarr.surveyengine.model.exposed.NavigationDirection
import com.qlarr.surveyengine.model.exposed.NavigationMode
import com.qlarr.surveyengine.model.exposed.SurveyMode
import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.scriptengine.getNavigate
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelevantValueTest {

    private val processed = ValidationUseCaseWrapper.create(design().toString()).validate()

    @Test
    fun relevant_value_is_the_value_when_relevant() {
        val output = navigate("""{"Q1.value":"x","Q2.value":"hello","Q3.value":7}""")
        assertEquals(JsonPrimitive("hello"), variable(output, "Q2", "relevant_value"))
        assertEquals(JsonPrimitive(7), variable(output, "Q3", "relevant_value"))
    }

    @Test
    fun relevant_value_is_undefined_when_irrelevant() {
        val output = navigate("""{"Q1.value":"y","Q2.value":"hello","Q3.value":7}""")
        assertEquals(JsonPrimitive("hello"), variable(output, "Q2", "value"))
        assertNull(variable(output, "Q2", "relevant_value"))
        assertNull(variable(output, "Q3", "relevant_value"))
    }

    @Test
    fun relevant_value_can_be_referenced_by_other_instructions() {
        assertEquals(JsonPrimitive(true), variable(navigate("""{"Q1.value":"x","Q2.value":"hello"}"""), "Q4", "relevance"))
        assertEquals(JsonPrimitive(false), variable(navigate("""{"Q1.value":"y","Q2.value":"hello"}"""), "Q4", "relevance"))
        val q4Relevance = instruction("G2", "Q4", "conditional_relevance")
        assertTrue(q4Relevance["errors"]?.jsonArray.isNullOrEmpty())
    }

    @Test
    fun relevant_value_keeps_the_value_return_type() {
        assertEquals(JsonPrimitive("int"), instruction("G1", "Q3", "relevant_value")["returnType"])
    }

    @Test
    fun relevant_value_is_only_added_to_components_with_a_value() {
        assertNull(survey()["groups"]!!.jsonArray[0].jsonObject["instructionList"]
            ?.jsonArray?.firstOrNull { it.jsonObject["code"]?.jsonPrimitive?.content == "relevant_value" })
    }

    @Test
    fun relevant_value_is_not_saved() {
        val output = navigate("""{"Q1.value":"x","Q2.value":"hello"}""")
        assertFalse(output.toSave.keys.any { it.endsWith(".relevant_value") })
    }

    @Test
    fun relevant_value_runtime_script_is_not_coerced_to_its_return_type() {
        val script = jsonMapper.parseToJsonElement(processed).jsonObject["script"]!!.jsonPrimitive.content
        assertTrue(script.contains("relevant_value: function(state) {return state.Q3.relevance ? state.Q3.value : undefined;}"))
    }

    private fun survey() = jsonMapper.parseToJsonElement(processed).jsonObject["survey"]!!.jsonObject

    private fun instruction(groupCode: String, questionCode: String, code: String): JsonObject =
        survey()["groups"]!!.jsonArray
            .map { it.jsonObject }
            .first { it["code"]!!.jsonPrimitive.content == groupCode }["questions"]!!.jsonArray
            .map { it.jsonObject }
            .first { it["code"]!!.jsonPrimitive.content == questionCode }["instructionList"]!!.jsonArray
            .map { it.jsonObject }
            .first { it["code"]!!.jsonPrimitive.content == code }

    private fun navigate(values: String): NavigationJsonOutput {
        val output = NavigationUseCaseWrapper.init(
            values = values,
            processedSurvey = processed,
            navigationMode = NavigationMode.ALL_IN_ONE,
            navigationIndex = null,
            navigationDirection = NavigationDirection.Start,
            skipInvalid = true,
            surveyMode = SurveyMode.ONLINE
        ).navigate(getNavigate())
        return jsonMapper.decodeFromString(NavigationJsonOutput.serializer(), output)
    }

    private fun variable(output: NavigationJsonOutput, code: String, name: String): JsonElement? =
        output.state["qlarrVariables"]!!.jsonObject[code]?.jsonObject?.get(name)

    private fun relevance(text: String) = buildJsonObject {
        put("code", "conditional_relevance")
        put("text", text)
        put("returnType", "boolean")
        put("isActive", true)
    }

    private fun value(returnType: String) = buildJsonObject {
        put("code", "value")
        put("text", "")
        put("returnType", returnType)
        put("isActive", false)
    }

    private fun question(code: String, returnType: String, vararg instructions: JsonObject) = buildJsonObject {
        put("code", code)
        put("type", "text")
        put("instructionList", buildJsonArray {
            add(value(returnType))
            instructions.forEach { add(it) }
        })
    }

    private fun group(code: String, groupType: String, vararg questions: JsonObject) = buildJsonObject {
        put("code", code)
        put("groupType", groupType)
        put("questions", JsonArray(questions.toList()))
    }

    private fun design() = buildJsonObject {
        put("code", "Survey")
        put("defaultLang", buildJsonObject {
            put("code", "en")
            put("name", "English")
        })
        put("navigationMode", "all_in_one")
        put("groups", buildJsonArray {
            add(
                group(
                    "G1", "GROUP",
                    question("Q1", "string"),
                    question("Q2", "string", relevance("""Q1.value == "x"""")),
                    question("Q3", "int", relevance("""Q1.value == "x""""))
                )
            )
            add(group("G2", "GROUP", question("Q4", "string", relevance("""Q2.relevant_value == "hello""""))))
            add(group("G3", "END"))
        })
    }
}
