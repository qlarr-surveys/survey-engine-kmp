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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QuotaNavigationTest {

    private val processedSurvey: String by lazy {
        ValidationUseCaseWrapper.create(design().toString()).validate()
    }

    @Test
    fun quota_instruction_is_valid_and_saved() {
        val output = navigate(values = "{}", direction = NavigationDirection.Start)
        assertEquals(NavigationIndex.Group("G1"), output.navigationIndex)
        assertEquals(JsonPrimitive(false), output.toSave["Survey.quota_QT1"])
    }

    @Test
    fun matching_a_full_quota_screens_out_to_end() {
        val output = navigate(
            values = """{"Q1.value":"male"}""",
            direction = NavigationDirection.Next,
            index = NavigationIndex.Group("G1"),
            fullQuotas = """["QT1"]"""
        )
        assertEquals(NavigationIndex.End("G3"), output.navigationIndex)
        assertEquals(JsonPrimitive(true), output.toSave["Survey.disqualified"])
        assertEquals(JsonPrimitive(true), output.toSave["Survey.quota_QT1"])
    }

    @Test
    fun matching_an_open_quota_does_not_screen_out() {
        val output = navigate(
            values = """{"Q1.value":"male"}""",
            direction = NavigationDirection.Next,
            index = NavigationIndex.Group("G1"),
            fullQuotas = """["QT2"]"""
        )
        assertEquals(NavigationIndex.Group("G2"), output.navigationIndex)
        assertEquals(JsonPrimitive(false), output.toSave["Survey.disqualified"])
        assertEquals(JsonPrimitive(true), output.toSave["Survey.quota_QT1"])
    }

    @Test
    fun a_quota_filling_later_screens_out_on_the_next_forward_move() {
        val output = navigate(
            values = """{"Q1.value":"male","Q2.value":"x"}""",
            direction = NavigationDirection.Next,
            index = NavigationIndex.Group("G2"),
            fullQuotas = """["QT1"]"""
        )
        assertEquals(NavigationIndex.End("G3"), output.navigationIndex)
        assertEquals(JsonPrimitive(true), output.toSave["Survey.disqualified"])
    }

    @Test
    fun switching_into_a_full_quota_screens_out() {
        val output = navigate(
            values = """{"Q1.value":"female"}""",
            direction = NavigationDirection.Next,
            index = NavigationIndex.Group("G1"),
            fullQuotas = """["QT2"]"""
        )
        assertEquals(NavigationIndex.End("G3"), output.navigationIndex)
        assertEquals(JsonPrimitive(true), output.toSave["Survey.disqualified"])
    }

    @Test
    fun switching_out_of_a_full_quota_reverses_the_screen_out() {
        val output = navigate(
            values = """{"Q1.value":"female"}""",
            direction = NavigationDirection.Next,
            index = NavigationIndex.Group("G1"),
            fullQuotas = """["QT1"]"""
        )
        assertEquals(NavigationIndex.Group("G2"), output.navigationIndex)
        assertEquals(JsonPrimitive(false), output.toSave["Survey.disqualified"])
        assertEquals(JsonPrimitive(false), output.toSave["Survey.quota_QT1"])
        assertEquals(JsonPrimitive(true), output.toSave["Survey.quota_QT2"])
    }

    @Test
    fun previous_does_not_check_quotas() {
        val output = navigate(
            values = """{"Q1.value":"male","Q2.value":"x"}""",
            direction = NavigationDirection.Previous,
            index = NavigationIndex.Group("G2"),
            fullQuotas = """["QT1"]"""
        )
        assertEquals(NavigationIndex.Group("G1"), output.navigationIndex)
        assertEquals(JsonPrimitive(false), output.toSave["Survey.disqualified"])
    }

    @Test
    fun no_quota_history_is_persisted() {
        val output = navigate(
            values = """{"Q1.value":"male"}""",
            direction = NavigationDirection.Next,
            index = NavigationIndex.Group("G1")
        )
        assertTrue(output.toSave.keys.none { it == "Survey.passed_quotas" })
    }

    @Test
    fun survey_without_quotas_does_not_save_quota_keys() {
        val noQuotas = ValidationUseCaseWrapper.create(design(withQuotas = false).toString()).validate()
        val output = navigate(values = "{}", direction = NavigationDirection.Start, survey = noQuotas)
        assertTrue(output.toSave.keys.none { it.contains("quota") })
    }

    @Test
    fun malformed_full_quotas_is_rejected() {
        assertFailsWith<MalformedFullQuotasException> {
            navigate(values = "{}", direction = NavigationDirection.Start, fullQuotas = "not json")
        }
    }

    @Test
    fun quota_outside_the_survey_is_a_design_error() {
        val badDesign = buildJsonObject {
            design().forEach { (key, value) ->
                if (key == "groups") {
                    put("groups", buildJsonArray {
                        add(group("G1", "GROUP", buildJsonObject {
                            textQuestion("Q1").forEach { (qKey, qValue) ->
                                if (qKey == "instructionList") {
                                    put("instructionList", buildJsonArray {
                                        qValue.jsonArray.forEach { add(it) }
                                        add(quota("QT3", "true"))
                                    })
                                } else put(qKey, qValue)
                            }
                        }))
                        add(group("G2", "GROUP", textQuestion("Q2")))
                        add(group("G3", "END"))
                    })
                } else put(key, value)
            }
        }
        val validated = ValidationUseCaseWrapper.create(badDesign.toString()).validate()
        assertTrue(validated.contains("QuotaNotOnSurvey"), "expected QuotaNotOnSurvey error in: $validated")
    }

    @Test
    fun renaming_a_question_updates_quota_condition() {
        val renamed = ChangeCodeUseCaseWrapper.create(processedSurvey).changeCode("Q1", "Qgender")
        val quotaTexts = jsonMapper.parseToJsonElement(renamed).jsonObject["survey"]!!.jsonObject["instructionList"]!!
            .jsonArray.map { it.jsonObject }
            .filter { it["code"]!!.jsonPrimitive.content.startsWith("quota_") }
            .map { it["text"]!!.jsonPrimitive.content }
        assertEquals(listOf("Qgender.value == \"male\"", "Qgender.value == \"female\""), quotaTexts)
    }

    private fun navigate(
        values: String,
        direction: NavigationDirection,
        index: NavigationIndex? = null,
        fullQuotas: String = "[]",
        survey: String = processedSurvey
    ): NavigationJsonOutput {
        val output = NavigationUseCaseWrapper.init(
            values = values,
            processedSurvey = survey,
            navigationMode = NavigationMode.GROUP_BY_GROUP,
            navigationIndex = index,
            navigationDirection = direction,
            skipInvalid = true,
            surveyMode = SurveyMode.ONLINE,
            fullQuotas = fullQuotas
        ).navigate(getNavigate())
        return jsonMapper.decodeFromString(NavigationJsonOutput.serializer(), output)
    }

    private fun textQuestion(code: String) = buildJsonObject {
        put("code", code)
        put("type", "text")
        put("instructionList", buildJsonArray {
            addJsonObject {
                put("code", "value")
                put("text", "")
                put("returnType", "string")
                put("isActive", false)
            }
        })
    }

    private fun group(code: String, groupType: String, vararg questions: JsonObject) = buildJsonObject {
        put("code", code)
        put("groupType", groupType)
        put("questions", JsonArray(questions.toList()))
    }

    private fun quota(code: String, text: String) = buildJsonObject {
        put("code", "quota_$code")
        put("text", text)
        put("returnType", "boolean")
        put("isActive", true)
    }

    private fun design(withQuotas: Boolean = true) = buildJsonObject {
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
        put("instructionList", buildJsonArray {
            if (withQuotas) {
                add(quota("QT1", "Q1.value == \"male\""))
                add(quota("QT2", "Q1.value == \"female\""))
            }
        })
        put("groups", buildJsonArray {
            add(group("G1", "GROUP", textQuestion("Q1")))
            add(group("G2", "GROUP", textQuestion("Q2")))
            add(group("G3", "END"))
        })
    }
}
