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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The engine places a screen-out skip for each quota listed in `Survey.quotas` on the last question its
 * `var_<code>_met` reads (and on earlier ones for when every later one is hidden), waiting until
 * those questions are answered or hidden.
 * Survey: G1(Q1), G2(Q2), G3(Q3), G4 end.
 */
class QuotaSkipTest {

    @Test
    fun skip_is_placed_on_the_last_question_the_quota_reads() {
        val survey = validate(design("QT1" to """Q1.value == "male" && Q2.value == "young""""))
        assertNull(skip(survey, "Q1", "QT1"))
        assertNull(skip(survey, "Q3", "QT1"))
        val skip = skip(survey, "Q2", "QT1")!!
        assertEquals("G4", skip["skipToComponent"]!!.jsonPrimitive.content)
        assertEquals(true, skip["disqualify"]!!.jsonPrimitive.boolean)
        assertNull(skip["errors"])
    }

    @Test
    fun earlier_question_gets_the_skip_only_for_when_later_ones_are_hidden() {
        val survey = validate(
            design("QT1" to """Q1.value == "male" && Q2.value != "young"""", q2Relevance = """Q1.value != "male"""")
        )
        assertTrue(skip(survey, "Q1", "QT1")!!["text"]!!.jsonPrimitive.content.endsWith("&& (!Q2.conditional_relevance)"))
        // Q2 shown and answered: only its own skip fires, so its page isn't skipped while the respondent is on it
        val output = navigate(survey, """{"Q1.value":"female","Q2.value":"old","Survey.var_QT1_full":true}""", "G2")
        assertEquals(NavigationIndex.Group("G3"), output.navigationIndex)
    }

    @Test
    fun full_met_quota_screens_out_once_its_last_question_is_answered() {
        val survey = validate(design("QT1" to """Q1.value == "male" && Q2.value == "young""""))
        val afterQ1 = navigate(survey, """{"Q1.value":"male","Survey.var_QT1_full":true}""", "G1")
        assertEquals(NavigationIndex.Group("G2"), afterQ1.navigationIndex)

        val afterQ2 = navigate(survey, """{"Q1.value":"male","Q2.value":"young","Survey.var_QT1_full":true}""", "G2")
        assertEquals(NavigationIndex.End("G4"), afterQ2.navigationIndex)
        assertEquals(JsonPrimitive(true), afterQ2.toSave["Survey.disqualified"])
    }

    @Test
    fun open_quota_does_not_screen_out() {
        val survey = validate(design("QT1" to """Q1.value == "male""""))
        val output = navigate(survey, """{"Q1.value":"male"}""", "G1")
        assertEquals(NavigationIndex.Group("G2"), output.navigationIndex)
        assertEquals(JsonPrimitive(false), output.toSave["Survey.disqualified"])
    }

    @Test
    fun negated_condition_waits_for_its_question_to_be_answered() {
        // reaching Q2 before Q1 (here by jumping) is what randomized groups do
        val survey = validate(design("QT1" to """Q1.value != "student" && Q2.value == "female""""))
        val q1Unanswered = navigate(survey, """{"Q2.value":"female","Survey.var_QT1_full":true}""", "G2")
        assertEquals(NavigationIndex.Group("G3"), q1Unanswered.navigationIndex)
        assertEquals(JsonPrimitive(false), q1Unanswered.toSave["Survey.disqualified"])

        val q1Answered =
            navigate(survey, """{"Q1.value":"worker","Q2.value":"female","Survey.var_QT1_full":true}""", "G2")
        assertEquals(NavigationIndex.End("G4"), q1Answered.navigationIndex)
    }

    @Test
    fun or_condition_waits_for_every_question_it_reads() {
        // accepted trade-off: an optional question left blank keeps the respondent in (over quota)
        // rather than risking an early screen-out
        val survey = validate(design("QT1" to """Q1.value == "male" || Q2.value == "young""""))
        val afterQ1 = navigate(survey, """{"Q1.value":"male","Survey.var_QT1_full":true}""", "G1")
        assertEquals(NavigationIndex.Group("G2"), afterQ1.navigationIndex)

        val afterQ2 = navigate(survey, """{"Q1.value":"male","Q2.value":"old","Survey.var_QT1_full":true}""", "G2")
        assertEquals(NavigationIndex.End("G4"), afterQ2.navigationIndex)
    }

    @Test
    fun hidden_last_question_does_not_hold_the_skip_back() {
        val survey = validate(
            design("QT1" to """Q1.value == "male" && Q2.value != "young"""", q2Relevance = """Q1.value != "male"""")
        )
        val output = navigate(survey, """{"Q1.value":"male","Survey.var_QT1_full":true}""", "G1")
        assertEquals(NavigationIndex.End("G4"), output.navigationIndex)
    }

    @Test
    fun quota_reading_through_another_variable_is_placed_on_the_question_behind_it() {
        val survey = validate(
            design("QT1" to "Survey.var_young", extraVariables = mapOf("young" to """Q3.value == "young""""))
        )
        assertTrue(skip(survey, "Q3", "QT1") != null)
    }

    @Test
    fun quota_reading_no_question_is_placed_on_the_first_question() {
        val survey = validate(design("QT1" to """Survey.lang == "en""""))
        assertTrue(skip(survey, "Q1", "QT1") != null)
        val output = navigate(survey, """{"Survey.var_QT1_full":true}""", "G1")
        assertEquals(NavigationIndex.End("G4"), output.navigationIndex)
    }

    @Test
    fun each_quota_gets_its_own_skip() {
        val survey = validate(design("QT1" to """Q1.value == "male"""", "QT2" to """Q1.value == "female""""))
        // Q1 is the only question either quota reads
        assertTrue(skip(survey, "Q1", "QT1") != null)
        assertTrue(skip(survey, "Q1", "QT2") != null)
        val output = navigate(survey, """{"Q1.value":"female","Survey.var_QT2_full":true}""", "G1")
        assertEquals(NavigationIndex.End("G4"), output.navigationIndex)
    }

    @Test
    fun quota_skips_are_rebuilt_on_every_validation() {
        val survey = validate(design("QT1" to """Q1.value == "male"""", staleSkipOn = "Q3"))
        assertNull(skip(survey, "Q3", "OLD"))
        val revalidated = validate(survey)
        assertEquals(skip(survey, "Q1", "QT1"), skip(revalidated, "Q1", "QT1"))
    }

    @Test
    fun quota_without_a_met_variable_gets_no_skip() {
        val survey = validate(design("QT1" to null))
        assertTrue(questions(survey).none { question ->
            question["instructionList"]!!.jsonArray.any {
                it.jsonObject["code"]!!.jsonPrimitive.content.startsWith("skip_to_quota_")
            }
        })
    }

    @Test
    fun quota_over_randomized_groups_is_valid_and_runs() {
        val survey = validate(design("QT1" to """Q2.value == "a" && Q3.value != "b"""", randomize = true))
        assertNull(skip(survey, "Q3", "QT1")!!["errors"])
        // G2 shown before G3, whatever order Start drew
        val order = """"G2.order":1,"G3.order":2"""
        val q3Unanswered = navigate(survey, """{$order,"Q2.value":"a","Survey.var_QT1_full":true}""", "G2")
        assertEquals(NavigationIndex.Group("G3"), q3Unanswered.navigationIndex)
        assertEquals(JsonPrimitive(false), q3Unanswered.toSave["Survey.disqualified"])
        val q3Answered =
            navigate(survey, """{$order,"Q2.value":"a","Q3.value":"c","Survey.var_QT1_full":true}""", "G3")
        assertEquals(NavigationIndex.End("G4"), q3Answered.navigationIndex)
    }

    private fun validate(design: JsonObject): JsonObject {
        val output = ValidationUseCaseWrapper.create(design.toString()).validate()
        return jsonMapper.parseToJsonElement(output).jsonObject["survey"]!!.jsonObject
    }

    private fun questions(survey: JsonObject) = survey["groups"]!!.jsonArray
        .flatMap { it.jsonObject["questions"]?.jsonArray ?: JsonArray(listOf()) }
        .map { it.jsonObject }

    private fun skip(survey: JsonObject, question: String, quota: String): JsonObject? =
        questions(survey).first { it["code"]!!.jsonPrimitive.content == question }["instructionList"]!!.jsonArray
            .map { it.jsonObject }
            .firstOrNull { it["code"]!!.jsonPrimitive.content == "skip_to_quota_$quota" }

    private fun navigate(survey: JsonObject, values: String, group: String): NavigationJsonOutput {
        val processed = ValidationUseCaseWrapper.create(survey.toString()).validate()
        val output = NavigationUseCaseWrapper.init(
            values = values,
            processedSurvey = processed,
            navigationMode = NavigationMode.GROUP_BY_GROUP,
            navigationIndex = NavigationIndex.Group(group),
            navigationDirection = NavigationDirection.Next,
            skipInvalid = true,
            surveyMode = SurveyMode.ONLINE
        ).navigate(getNavigate())
        return jsonMapper.decodeFromString(NavigationJsonOutput.serializer(), output)
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

    private fun variable(code: String, text: String, isActive: Boolean = true) = buildJsonObject {
        put("code", "var_$code")
        put("text", text)
        put("returnType", "boolean")
        put("isActive", isActive)
    }

    private fun design(
        vararg quotas: Pair<String, String?>,
        extraVariables: Map<String, String> = mapOf(),
        q2Relevance: String? = null,
        staleSkipOn: String? = null,
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
        put("quotas", buildJsonArray {
            quotas.forEach { (code, _) -> addJsonObject { put("code", code) } }
        })
        put("instructionList", buildJsonArray {
            quotas.forEach { (code, condition) ->
                if (condition != null) {
                    add(variable("${code}_met", condition))
                    add(variable("${code}_full", "false", isActive = false))
                }
            }
            extraVariables.forEach { (code, text) -> add(variable(code, text)) }
            if (randomize) {
                addJsonObject {
                    put("code", "random_group")
                    put("groups", buildJsonArray {
                        addJsonObject { put("codes", buildJsonArray { add("G2"); add("G3") }) }
                    })
                }
            }
        })
        val staleSkip = buildJsonObject {
            put("code", "skip_to_quota_OLD")
            put("skipToComponent", "G4")
            put("toEnd", false)
            put("disqualify", true)
            put("text", "true")
            put("returnType", "boolean")
            put("isActive", true)
        }
        fun question(code: String, relevance: String? = null) =
            if (code == staleSkipOn) textQuestion(code, relevance, staleSkip) else textQuestion(code, relevance)
        put("groups", buildJsonArray {
            add(group("G1", "GROUP", question("Q1")))
            add(group("G2", "GROUP", question("Q2", q2Relevance)))
            add(group("G3", "GROUP", question("Q3")))
            add(group("G4", "END"))
        })
    }
}
