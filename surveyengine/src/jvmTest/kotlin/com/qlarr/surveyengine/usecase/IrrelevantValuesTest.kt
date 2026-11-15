package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.model.NavigationJsonOutput
import com.qlarr.surveyengine.model.exposed.NavigationDirection
import com.qlarr.surveyengine.model.exposed.NavigationIndex
import com.qlarr.surveyengine.model.exposed.NavigationMode
import com.qlarr.surveyengine.model.exposed.SurveyMode
import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.scriptengine.getNavigate
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * On completion, values of irrelevant components (directly or through an
 * irrelevant parent) are left out of toSave.
 */
class IrrelevantValuesTest {

    private val processed = ValidationUseCaseWrapper.create(design().toString()).validate()

    @Test
    fun irrelevant_values_are_not_saved_on_completion() {
        val output = navigate(
            """{"Q1.value":"y","Q2.value":"hello","Q3.value":7}""",
            NavigationIndex.Group("G1"),
            NavigationDirection.Next
        )
        assertEquals(NavigationIndex.End("G3"), output.navigationIndex)
        assertEquals(JsonPrimitive("y"), output.toSave["Q1.value"])
        assertFalse(output.toSave.containsKey("Q2.value"))
        assertEquals(JsonPrimitive(false), output.toSave["Q2.relevance"])
        assertFalse(output.toSave.containsKey("Q3.value"))
    }

    @Test
    fun relevant_values_are_saved_on_completion() {
        val output = navigate(
            """{"Q1.value":"x","Q2.value":"hello","Q3.value":7}""",
            NavigationIndex.Group("G2"),
            NavigationDirection.Next
        )
        assertEquals(NavigationIndex.End("G3"), output.navigationIndex)
        assertEquals(JsonPrimitive("hello"), output.toSave["Q2.value"])
        assertEquals(JsonPrimitive(7), output.toSave["Q3.value"])
    }

    @Test
    fun irrelevant_values_are_kept_before_completion() {
        val output = navigate(
            """{"Q1.value":"y","Q2.value":"hello","Q3.value":7}""",
            null,
            NavigationDirection.Start
        )
        assertTrue(output.navigationIndex !is NavigationIndex.End)
        assertEquals(JsonPrimitive("hello"), output.toSave["Q2.value"])
        assertEquals(JsonPrimitive(7), output.toSave["Q3.value"])
    }

    private fun navigate(
        values: String,
        navigationIndex: NavigationIndex?,
        navigationDirection: NavigationDirection
    ): NavigationJsonOutput {
        val output = NavigationUseCaseWrapper.init(
            values = values,
            processedSurvey = processed,
            navigationMode = NavigationMode.GROUP_BY_GROUP,
            navigationIndex = navigationIndex,
            navigationDirection = navigationDirection,
            skipInvalid = true,
            surveyMode = SurveyMode.ONLINE
        ).navigate(getNavigate())
        return jsonMapper.decodeFromString(NavigationJsonOutput.serializer(), output)
    }

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

    private fun group(code: String, groupType: String, vararg questions: JsonObject, instructions: List<JsonObject> = listOf()) =
        buildJsonObject {
            put("code", code)
            put("groupType", groupType)
            put("instructionList", JsonArray(instructions))
            put("questions", JsonArray(questions.toList()))
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
            add(
                group(
                    "G1", "GROUP",
                    question("Q1", "string"),
                    question("Q2", "string", relevance("""Q1.value == "x""""))
                )
            )
            add(group("G2", "GROUP", question("Q3", "int"), instructions = listOf(relevance("""Q1.value == "x""""))))
            add(group("G3", "END"))
        })
    }
}
