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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuotaTest {

    @Test
    fun quota_gets_a_full_input() {
        val survey = validate(design(quota("QT1", """Q1.value == "male"""")))
        assertEquals(false, surveyInstruction(survey, "is_quota_met_QT1")!!["isActive"]!!.jsonPrimitive.boolean)
        assertNull(surveyInstruction(survey, "quota_QT1")!!["errors"])
        assertTrue(questions(survey).none { question ->
            question["instructionList"]!!.jsonArray.any { it.jsonObject["code"]!!.jsonPrimitive.content.startsWith("skip_to") }
        })
    }

    @Test
    fun quota_is_saved_and_its_full_input_is_not() {
        val output = navigate(validate(design(quota("QT1", """Q1.value == "male""""))), """{"Q1.value":"male"}""", "G1")
        assertEquals(JsonPrimitive(true), output.toSave["Survey.quota_QT1"])
        assertFalse(output.toSave.containsKey("Survey.is_quota_met_QT1"))
    }

    @Test
    fun full_input_of_the_wrong_type_is_ignored() {
        val survey = validate(design(quota("QT1", """Q1.value == "male"""")))
        listOf("\"true\"", "1", "null").forEach { flag ->
            val output = navigate(survey, """{"Q1.value":"male","Survey.is_quota_met_QT1":$flag}""", "G1")
            assertEquals(NavigationIndex.Group("G2"), output.navigationIndex, "flag $flag")
        }
    }

    @Test
    fun quota_sent_in_with_the_values_is_ignored() {
        val survey = validate(design(quota("QT1", """Q1.value == "male"""")))
        val output = navigate(survey, """{"Q1.value":"female","Survey.quota_QT1":true,"Survey.is_quota_met_QT1":true}""", "G1")
        assertEquals(JsonPrimitive(false), output.toSave["Survey.quota_QT1"])
        assertEquals(NavigationIndex.Group("G2"), output.navigationIndex)
    }

    @Test
    fun full_met_quota_screens_out_once_its_questions_are_behind() {
        val survey = validate(design(quota("QT1", """Q1.value == "male" && Q2.value == "young"""")))
        val afterQ1 = navigate(survey, """{"Q1.value":"male","Survey.is_quota_met_QT1":true}""", "G1")
        assertEquals(NavigationIndex.Group("G2"), afterQ1.navigationIndex)

        val afterQ2 = navigate(survey, """{"Q1.value":"male","Q2.value":"young","Survey.is_quota_met_QT1":true}""", "G2")
        assertEquals(NavigationIndex.End("G4"), afterQ2.navigationIndex)
        assertEquals(JsonPrimitive(true), afterQ2.toSave["Survey.disqualified"])
        assertEquals(JsonPrimitive("QT1"), afterQ2.toSave["Survey.screened_out_by_quota"])
    }

    @Test
    fun open_quota_does_not_screen_out() {
        val output = navigate(validate(design(quota("QT1", """Q1.value == "male""""))), """{"Q1.value":"male"}""", "G1")
        assertEquals(NavigationIndex.Group("G2"), output.navigationIndex)
        assertEquals(JsonPrimitive(false), output.toSave["Survey.disqualified"])
        assertFalse(output.toSave.containsKey("Survey.screened_out_by_quota"))
    }

    @Test
    fun or_condition_waits_until_every_question_it_reads_is_behind() {
        val survey = validate(design(quota("QT1", """Q1.value == "male" || Q2.value == "young"""")))
        val afterQ1 = navigate(survey, """{"Q1.value":"male","Survey.is_quota_met_QT1":true}""", "G1")
        assertEquals(NavigationIndex.Group("G2"), afterQ1.navigationIndex)

        val afterQ2 = navigate(survey, """{"Q1.value":"male","Survey.is_quota_met_QT1":true}""", "G2")
        assertEquals(NavigationIndex.End("G4"), afterQ2.navigationIndex)
        assertEquals(JsonPrimitive(true), afterQ2.toSave["Survey.disqualified"])
    }

    @Test
    fun negated_condition_waits_for_its_question_in_randomized_order() {
        val survey = validate(design(quota("QT1", """Q2.value == "a" && Q3.value != "b""""), randomize = true))
        val order = """"G2.order":2,"G3.order":1"""
        val afterQ3 = navigate(survey, """{$order,"Q3.value":"c","Survey.is_quota_met_QT1":true}""", "G3")
        assertEquals(NavigationIndex.Group("G2"), afterQ3.navigationIndex)
        assertEquals(JsonPrimitive(false), afterQ3.toSave["Survey.disqualified"])

        val afterQ2 = navigate(survey, """{$order,"Q2.value":"a","Q3.value":"c","Survey.is_quota_met_QT1":true}""", "G2")
        assertEquals(NavigationIndex.End("G4"), afterQ2.navigationIndex)
        assertEquals(JsonPrimitive(true), afterQ2.toSave["Survey.disqualified"])
    }

    @Test
    fun negated_question_still_ahead_holds_the_screen_out_back() {
        val survey = validate(design(quota("QT1", """Q2.value != "x" && Q3.value == "c""""), randomize = true))
        val order = """"G2.order":2,"G3.order":1"""
        val afterQ3 = navigate(survey, """{$order,"Q3.value":"c","Survey.is_quota_met_QT1":true}""", "G3")
        assertEquals(NavigationIndex.Group("G2"), afterQ3.navigationIndex)

        val afterQ2 = navigate(survey, """{$order,"Q2.value":"x","Q3.value":"c","Survey.is_quota_met_QT1":true}""", "G2")
        assertEquals(NavigationIndex.End("G4"), afterQ2.navigationIndex)
        assertEquals(JsonPrimitive(false), afterQ2.toSave["Survey.disqualified"])
    }

    @Test
    fun hidden_question_does_not_hold_the_screen_out_back() {
        val survey = validate(
            design(quota("QT1", """Q1.value == "male" && Q3.value != "young""""), q3Relevance = """Q1.value != "male"""")
        )
        val output = navigate(survey, """{"Q1.value":"male","Survey.is_quota_met_QT1":true}""", "G1")
        assertEquals(NavigationIndex.End("G4"), output.navigationIndex)
        assertEquals(JsonPrimitive(true), output.toSave["Survey.disqualified"])
    }

    @Test
    fun question_jumped_over_by_a_skip_does_not_hold_the_screen_out_back() {
        val survey = validate(
            design(quota("QT1", """Q1.value == "male" && Q2.value != "young""""), skipOnQ1 = """Q1.value == "male"""")
        )
        val output = navigate(survey, """{"Q1.value":"male","Survey.is_quota_met_QT1":true}""", "G1")
        assertEquals(NavigationIndex.End("G4"), output.navigationIndex)
        assertEquals(JsonPrimitive(true), output.toSave["Survey.disqualified"])
    }

    @Test
    fun question_hidden_by_a_condition_still_ahead_holds_the_screen_out_back() {
        val survey = validate(
            design(quota("QT1", """Q1.value == "male" && Q3.value != "a""""), q3Relevance = """Q2.value == "yes"""")
        )
        val afterQ1 = navigate(survey, """{"Q1.value":"male","Survey.is_quota_met_QT1":true}""", "G1")
        assertEquals(NavigationIndex.Group("G2"), afterQ1.navigationIndex)
        assertEquals(JsonPrimitive(false), afterQ1.toSave["Survey.disqualified"])

        val hidden = navigate(survey, """{"Q1.value":"male","Q2.value":"no","Survey.is_quota_met_QT1":true}""", "G2")
        assertEquals(NavigationIndex.End("G4"), hidden.navigationIndex)
        assertEquals(JsonPrimitive(true), hidden.toSave["Survey.disqualified"])

        val shown = navigate(survey, """{"Q1.value":"male","Q2.value":"yes","Survey.is_quota_met_QT1":true}""", "G2")
        assertEquals(NavigationIndex.Group("G3"), shown.navigationIndex)
    }

    @Test
    fun quota_reading_no_question_screens_out_on_the_first_next() {
        val survey = validate(design(quota("QT1", """Survey.lang == "en"""")))
        val start = navigate(survey, """{"Survey.is_quota_met_QT1":true}""", null, NavigationDirection.Start)
        assertEquals(NavigationIndex.Group("G1"), start.navigationIndex)
        val output = navigate(survey, """{"Survey.is_quota_met_QT1":true}""", "G1")
        assertEquals(NavigationIndex.End("G4"), output.navigationIndex)
    }

    @Test
    fun only_moving_forward_screens_out() {
        val survey = validate(design(quota("QT1", """Q1.value == "male"""")))
        val values = """{"Q1.value":"male","Survey.is_quota_met_QT1":true}"""
        val previous = navigate(survey, values, "G2", NavigationDirection.Previous)
        assertEquals(NavigationIndex.Group("G1"), previous.navigationIndex)
        val jump = navigate(survey, values, "G1", NavigationDirection.Jump(NavigationIndex.Group("G3")))
        assertEquals(NavigationIndex.Group("G3"), jump.navigationIndex)
        assertEquals(JsonPrimitive(false), jump.toSave["Survey.disqualified"])
    }

    @Test
    fun submitting_with_a_full_met_quota_disqualifies() {
        val survey = validate(design(quota("QT1", """Q3.value == "x"""")))
        val output = navigate(survey, """{"Q3.value":"x","Survey.is_quota_met_QT1":true}""", "G3")
        assertEquals(NavigationIndex.End("G4"), output.navigationIndex)
        assertEquals(JsonPrimitive(true), output.toSave["Survey.disqualified"])
    }

    @Test
    fun each_quota_is_checked_on_its_own() {
        val survey = validate(design(quota("QT1", """Q1.value == "male""""), quota("QT2", """Q1.value == "female"""")))
        val open = navigate(survey, """{"Q1.value":"female","Survey.is_quota_met_QT1":true}""", "G1")
        assertEquals(NavigationIndex.Group("G2"), open.navigationIndex)
        val full = navigate(survey, """{"Q1.value":"female","Survey.is_quota_met_QT2":true}""", "G1")
        assertEquals(NavigationIndex.End("G4"), full.navigationIndex)
        assertEquals(JsonPrimitive("QT2"), full.toSave["Survey.screened_out_by_quota"])
    }

    @Test
    fun only_the_first_screening_quota_is_recorded() {
        val survey = validate(design(quota("QT1", """Q1.value == "male""""), quota("QT2", """Q1.value != "female"""")))
        val output = navigate(
            survey,
            """{"Q1.value":"male","Survey.is_quota_met_QT1":true,"Survey.is_quota_met_QT2":true}""",
            "G1"
        )
        assertEquals(NavigationIndex.End("G4"), output.navigationIndex)
        assertEquals(JsonPrimitive("QT1"), output.toSave["Survey.screened_out_by_quota"])
    }

    @Test
    fun disqualifying_skip_is_not_recorded_as_a_quota_screen_out() {
        val survey = validate(
            design(quota("QT1", """Q1.value == "male""""), disqualifyOnQ1 = """Q1.value == "male"""")
        )
        val output = navigate(survey, """{"Q1.value":"male","Survey.is_quota_met_QT1":true}""", "G1")
        assertEquals(NavigationIndex.End("G4"), output.navigationIndex)
        assertEquals(JsonPrimitive(true), output.toSave["Survey.disqualified"])
        assertFalse(output.toSave.containsKey("Survey.screened_out_by_quota"))
    }

    @Test
    fun broken_quota_has_errors_and_never_screens_out() {
        val survey = validate(design(quota("QT1", """Q9.value == "x"""")))
        assertTrue(surveyInstruction(survey, "quota_QT1")!!["errors"] != null)
        val output = navigate(survey, """{"Survey.is_quota_met_QT1":true}""", "G1")
        assertEquals(NavigationIndex.Group("G2"), output.navigationIndex)
    }

    @Test
    fun quota_outside_the_survey_is_an_error() {
        val survey = validate(design(quotaOnQ1 = quota("QT1", "true")))
        val misplaced = questions(survey).first { it["code"]!!.jsonPrimitive.content == "Q1" }["instructionList"]!!
            .jsonArray.map { it.jsonObject }.first { it["code"]!!.jsonPrimitive.content == "quota_QT1" }
        assertTrue(misplaced["errors"].toString().contains("QuotaNotOnSurvey"))
    }

    @Test
    fun renaming_a_question_updates_the_quota() {
        val processed = ValidationUseCaseWrapper.create(design(quota("QT1", """Q1.value == "male"""")).toString()).validate()
        val renamed = jsonMapper.parseToJsonElement(ChangeCodeUseCaseWrapper.create(processed).changeCode("Q1", "Qgender"))
            .jsonObject["survey"]!!.jsonObject
        assertEquals("""Qgender.value == "male"""", surveyInstruction(renamed, "quota_QT1")!!["text"]!!.jsonPrimitive.content)
    }

    private fun validate(design: JsonObject): JsonObject {
        val output = ValidationUseCaseWrapper.create(design.toString()).validate()
        return jsonMapper.parseToJsonElement(output).jsonObject["survey"]!!.jsonObject
    }

    private fun surveyInstruction(survey: JsonObject, code: String): JsonObject? = survey["instructionList"]!!.jsonArray
        .map { it.jsonObject }
        .firstOrNull { it["code"]!!.jsonPrimitive.content == code }

    private fun questions(survey: JsonObject) = survey["groups"]!!.jsonArray
        .flatMap { it.jsonObject["questions"]?.jsonArray ?: JsonArray(listOf()) }
        .map { it.jsonObject }

    private fun navigate(
        survey: JsonObject,
        values: String,
        group: String?,
        direction: NavigationDirection = NavigationDirection.Next
    ): NavigationJsonOutput {
        val processed = ValidationUseCaseWrapper.create(survey.toString()).validate()
        val output = NavigationUseCaseWrapper.init(
            values = values,
            processedSurvey = processed,
            navigationMode = NavigationMode.GROUP_BY_GROUP,
            navigationIndex = group?.let { NavigationIndex.Group(it) },
            navigationDirection = direction,
            skipInvalid = true,
            surveyMode = SurveyMode.ONLINE
        ).navigate(getNavigate())
        return jsonMapper.decodeFromString(NavigationJsonOutput.serializer(), output)
    }

    private fun quota(code: String, condition: String) = buildJsonObject {
        put("code", "quota_$code")
        put("text", condition)
        put("returnType", "boolean")
        put("isActive", true)
    }

    private fun textQuestion(code: String, relevance: String? = null, vararg extra: JsonObject) = buildJsonObject {
        put("code", code)
        put("type", "text")
        put("instructionList", buildJsonArray {
            addJsonObject {
                put("code", "value")
                put("text", "")
                put("returnType", "string")
                put("isActive", false)
            }
            if (relevance != null) {
                addJsonObject {
                    put("code", "conditional_relevance")
                    put("text", relevance)
                    put("returnType", "boolean")
                    put("isActive", true)
                }
            }
            extra.forEach { add(it) }
        })
    }

    private fun group(code: String, groupType: String, vararg questions: JsonObject) = buildJsonObject {
        put("code", code)
        put("groupType", groupType)
        put("questions", JsonArray(questions.toList()))
    }

    private fun design(
        vararg quotas: JsonObject,
        q3Relevance: String? = null,
        skipOnQ1: String? = null,
        disqualifyOnQ1: String? = null,
        quotaOnQ1: JsonObject? = null,
        randomize: Boolean = false
    ) = buildJsonObject {
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
            quotas.forEach { add(it) }
            if (randomize) {
                addJsonObject {
                    put("code", "random_group")
                    put("groups", buildJsonArray {
                        addJsonObject { put("codes", buildJsonArray { add("G2"); add("G3") }) }
                    })
                }
            }
        })
        val q1Extras = listOfNotNull(
            skipOnQ1?.let { text ->
                buildJsonObject {
                    put("code", "skip_to_G3")
                    put("skipToComponent", "G3")
                    put("toEnd", false)
                    put("disqualify", false)
                    put("text", text)
                    put("returnType", "boolean")
                    put("isActive", true)
                }
            },
            disqualifyOnQ1?.let { text ->
                buildJsonObject {
                    put("code", "skip_to_G4")
                    put("skipToComponent", "G4")
                    put("toEnd", false)
                    put("disqualify", true)
                    put("text", text)
                    put("returnType", "boolean")
                    put("isActive", true)
                }
            },
            quotaOnQ1
        )
        put("groups", buildJsonArray {
            add(group("G1", "GROUP", textQuestion("Q1", null, *q1Extras.toTypedArray())))
            add(group("G2", "GROUP", textQuestion("Q2")))
            add(group("G3", "GROUP", textQuestion("Q3", q3Relevance)))
            add(group("G4", "END"))
        })
    }
}
