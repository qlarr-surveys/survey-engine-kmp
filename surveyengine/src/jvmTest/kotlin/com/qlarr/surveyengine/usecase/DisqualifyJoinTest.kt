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

/**
 * A survey with TWO "skip to end + disqualify" rules, where the respondent
 * triggers only one of them.
 */
class DisqualifyJoinTest {

    private val processedSurvey: String by lazy {
        ValidationUseCaseWrapper.create(design().toString()).validate()
    }

    @Test
    fun generated_disqualified_expression() {
        val survey = jsonMapper.parseToJsonElement(processedSurvey).jsonObject["survey"]!!.jsonObject
        val text = survey["instructionList"]!!.jsonArray
            .map { it.jsonObject }
            .first { it["code"]!!.jsonPrimitive.content == "disqualified" }["text"]!!
            .jsonPrimitive.content
        println("DISQUALIFIED TEXT: $text")
    }

    @Test
    fun triggering_only_the_first_rule_disqualifies() {
        val output = navigate(values = """{"Q1.value":"dq"}""")
        println("NAV INDEX: ${output.navigationIndex}")
        println("DISQUALIFIED: ${output.toSave["Survey.disqualified"]}")
        assertEquals(NavigationIndex.End("G3"), output.navigationIndex)
        assertEquals(JsonPrimitive(true), output.toSave["Survey.disqualified"])
    }

    @Test
    fun triggering_neither_rule_does_not_disqualify() {
        val output = navigate(values = """{"Q1.value":"ok"}""")
        assertEquals(JsonPrimitive(false), output.toSave["Survey.disqualified"])
    }

    private fun navigate(values: String): NavigationJsonOutput {
        val output = NavigationUseCaseWrapper.init(
            values = values,
            processedSurvey = processedSurvey,
            navigationMode = NavigationMode.GROUP_BY_GROUP,
            navigationIndex = NavigationIndex.Group("G1"),
            navigationDirection = NavigationDirection.Next,
            skipInvalid = true,
            surveyMode = SurveyMode.ONLINE
        ).navigate(getNavigate())
        return jsonMapper.decodeFromString(NavigationJsonOutput.serializer(), output)
    }

    private fun questionWithDisqualifySkip(code: String) = buildJsonObject {
        put("code", code)
        put("type", "text")
        put("instructionList", buildJsonArray {
            addJsonObject {
                put("code", "value")
                put("text", "")
                put("returnType", "string")
                put("isActive", false)
            }
            addJsonObject {
                put("code", "skip_to_G3")
                put("skipToComponent", "G3")
                put("toEnd", false)
                put("disqualify", true)
                put("text", "$code.value == \"dq\"")
                put("returnType", "boolean")
                put("isActive", true)
            }
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
        put("navigationMode", "group_by_group")
        put("allowPrevious", true)
        put("skipInvalid", true)
        put("allowIncomplete", true)
        put("allowJump", true)
        put("groups", buildJsonArray {
            add(group("G1", "GROUP", questionWithDisqualifySkip("Q1")))
            add(group("G2", "GROUP", questionWithDisqualifySkip("Q2")))
            add(group("G3", "END"))
        })
    }
}
