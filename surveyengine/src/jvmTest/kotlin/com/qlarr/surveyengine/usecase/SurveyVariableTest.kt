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
 * Survey variables, set up the way the designer sets up a quota:
 * an active var_QTx_met computed from the answers, an inactive var_QTx_full passed in by the backend,
 * and a skip on Q1 that fires when a met quota is full.
 */
class SurveyVariableTest {

    private val processedSurvey: String by lazy {
        ValidationUseCaseWrapper.create(design().toString()).validate()
    }

    @Test
    fun active_variable_is_computed_and_saved() {
        val start = navigate(values = "{}", direction = NavigationDirection.Start)
        assertEquals(NavigationIndex.Group("G1"), start.navigationIndex)
        assertEquals(JsonPrimitive(false), start.toSave["Survey.var_QT1_met"])

        val next = navigate(
            values = """{"Q1.value":"male"}""",
            direction = NavigationDirection.Next,
            index = NavigationIndex.Group("G1")
        )
        assertEquals(JsonPrimitive(true), next.toSave["Survey.var_QT1_met"])
        assertEquals(JsonPrimitive(false), next.toSave["Survey.var_QT2_met"])
    }

    @Test
    fun variable_can_have_any_return_type() {
        val output = navigate(
            values = """{"Q1.value":"male"}""",
            direction = NavigationDirection.Next,
            index = NavigationIndex.Group("G1")
        )
        assertEquals(JsonPrimitive(4), output.toSave["Survey.var_answer_length"])
    }

    @Test
    fun input_variable_is_never_saved() {
        listOf("""{"Q1.value":"male"}""", """{"Q1.value":"male","Survey.var_QT2_full":true}""").forEach { values ->
            val output = navigate(
                values = values,
                direction = NavigationDirection.Next,
                index = NavigationIndex.Group("G1")
            )
            assertTrue(output.toSave.keys.none { it.endsWith("_full") }, "values $values")
        }
    }

    @Test
    fun input_variable_reaches_the_client_state() {
        val output = navigate(
            values = """{"Q1.value":"female","Survey.var_QT1_full":true}""",
            direction = NavigationDirection.Next,
            index = NavigationIndex.Group("G1")
        )
        val surveyState = output.state["qlarrVariables"]!!.jsonObject["Survey"]!!.jsonObject
        assertEquals(JsonPrimitive(true), surveyState["var_QT1_full"])
    }

    @Test
    fun matching_a_full_quota_screens_out_to_end() {
        val output = navigate(
            values = """{"Q1.value":"male","Survey.var_QT1_full":true}""",
            direction = NavigationDirection.Next,
            index = NavigationIndex.Group("G1")
        )
        assertEquals(NavigationIndex.End("G4"), output.navigationIndex)
        assertEquals(JsonPrimitive(true), output.toSave["Survey.disqualified"])
        assertEquals(JsonPrimitive(true), output.toSave["Survey.var_QT1_met"])
    }

    @Test
    fun matching_an_open_quota_does_not_screen_out() {
        val output = navigate(
            values = """{"Q1.value":"male","Survey.var_QT2_full":true}""",
            direction = NavigationDirection.Next,
            index = NavigationIndex.Group("G1")
        )
        assertEquals(NavigationIndex.Group("G2"), output.navigationIndex)
        assertEquals(JsonPrimitive(false), output.toSave["Survey.disqualified"])
    }

    @Test
    fun input_falls_back_to_its_default_when_not_passed() {
        val output = navigate(
            values = """{"Q1.value":"male"}""",
            direction = NavigationDirection.Next,
            index = NavigationIndex.Group("G1")
        )
        assertEquals(NavigationIndex.Group("G2"), output.navigationIndex)
        assertEquals(JsonPrimitive(false), output.toSave["Survey.disqualified"])
    }

    @Test
    fun input_of_the_wrong_type_is_ignored() {
        listOf("\"true\"", "1", "null").forEach { flag ->
            val output = navigate(
                values = """{"Q1.value":"male","Survey.var_QT1_full":$flag}""",
                direction = NavigationDirection.Next,
                index = NavigationIndex.Group("G1")
            )
            assertEquals(NavigationIndex.Group("G2"), output.navigationIndex, "flag $flag")
        }
    }

    @Test
    fun required_question_after_the_skip_does_not_block_the_screen_out() {
        val survey = ValidationUseCaseWrapper.create(design(withRequired = true).toString()).validate()
        val output = navigate(
            values = """{"Q1.value":"male","Survey.var_QT1_full":true}""",
            direction = NavigationDirection.Next,
            index = NavigationIndex.Group("G1"),
            survey = survey,
            skipInvalid = false
        )
        assertEquals(NavigationIndex.End("G4"), output.navigationIndex)
        assertEquals(JsonPrimitive(true), output.toSave["Survey.disqualified"])
    }

    @Test
    fun full_quota_can_skip_to_another_group_without_disqualifying() {
        val survey = ValidationUseCaseWrapper.create(design(skipTo = "G3", disqualify = false).toString()).validate()
        val output = navigate(
            values = """{"Q1.value":"male","Survey.var_QT1_full":true}""",
            direction = NavigationDirection.Next,
            index = NavigationIndex.Group("G1"),
            survey = survey
        )
        assertEquals(NavigationIndex.Group("G3"), output.navigationIndex)
        assertEquals(JsonPrimitive(false), output.toSave["Survey.disqualified"])
    }

    @Test
    fun variable_referencing_a_deleted_question_is_not_evaluated() {
        val survey = ValidationUseCaseWrapper.create(design(withBrokenVariable = true).toString()).validate()
        assertTrue(survey.contains("unIdentified: Q9.value"), "expected var_broken to reference a missing question")
        val output = navigate(
            values = """{"Q1.value":"male"}""",
            direction = NavigationDirection.Next,
            index = NavigationIndex.Group("G1"),
            survey = survey
        )
        assertEquals(NavigationIndex.Group("G2"), output.navigationIndex)
        assertFalse(output.toSave.containsKey("Survey.var_broken"))
        assertEquals(JsonPrimitive(true), output.toSave["Survey.var_QT1_met"])
    }

    @Test
    fun variable_outside_the_survey_is_a_design_error() {
        val badDesign = buildJsonObject {
            design().forEach { (key, value) ->
                if (key == "groups") {
                    put("groups", buildJsonArray {
                        add(group("G1", "GROUP", buildJsonObject {
                            textQuestion("Q1").forEach { (qKey, qValue) ->
                                if (qKey == "instructionList") {
                                    put("instructionList", buildJsonArray {
                                        qValue.jsonArray.forEach { add(it) }
                                        add(variable("misplaced", "true"))
                                    })
                                } else put(qKey, qValue)
                            }
                        }))
                        add(group("G2", "GROUP", textQuestion("Q2")))
                        add(group("G4", "END"))
                    })
                } else put(key, value)
            }
        }
        val validated = ValidationUseCaseWrapper.create(badDesign.toString()).validate()
        assertTrue(validated.contains("VariableNotOnSurvey"), "expected VariableNotOnSurvey error in: $validated")
    }

    @Test
    fun renaming_a_question_updates_variable_text() {
        val renamed = ChangeCodeUseCaseWrapper.create(processedSurvey).changeCode("Q1", "Qgender")
        val metTexts = jsonMapper.parseToJsonElement(renamed).jsonObject["survey"]!!.jsonObject["instructionList"]!!
            .jsonArray.map { it.jsonObject }
            .filter { it["code"]!!.jsonPrimitive.content.endsWith("_met") }
            .map { it["text"]!!.jsonPrimitive.content }
        assertEquals(listOf("Qgender.value == \"male\"", "Qgender.value == \"female\""), metTexts)
    }

    private fun navigate(
        values: String,
        direction: NavigationDirection,
        index: NavigationIndex? = null,
        survey: String = processedSurvey,
        skipInvalid: Boolean = true
    ): NavigationJsonOutput {
        val output = NavigationUseCaseWrapper.init(
            values = values,
            processedSurvey = survey,
            navigationMode = NavigationMode.GROUP_BY_GROUP,
            navigationIndex = index,
            navigationDirection = direction,
            skipInvalid = skipInvalid,
            surveyMode = SurveyMode.ONLINE
        ).navigate(getNavigate())
        return jsonMapper.decodeFromString(NavigationJsonOutput.serializer(), output)
    }

    private fun textQuestion(code: String, required: Boolean = false, vararg extra: JsonObject) = buildJsonObject {
        put("code", code)
        put("type", "text")
        put("instructionList", buildJsonArray {
            addJsonObject {
                put("code", "value")
                put("text", "")
                put("returnType", "string")
                put("isActive", false)
            }
            if (required) {
                addJsonObject {
                    put("code", "validation_required")
                    put("text", "!!$code.value")
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

    private fun variable(code: String, text: String, returnType: String = "boolean", isActive: Boolean = true) =
        buildJsonObject {
            put("code", "var_$code")
            put("text", text)
            put("returnType", returnType)
            put("isActive", isActive)
        }

    private fun skip(to: String, disqualify: Boolean, text: String) = buildJsonObject {
        put("code", "skip_to_$to")
        put("skipToComponent", to)
        put("toEnd", false)
        put("disqualify", disqualify)
        put("text", text)
        put("returnType", "boolean")
        put("isActive", true)
    }

    private fun design(
        withRequired: Boolean = false,
        withBrokenVariable: Boolean = false,
        skipTo: String = "G4",
        disqualify: Boolean = true
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
            add(variable("QT1_met", "Q1.value == \"male\""))
            add(variable("QT2_met", "Q1.value == \"female\""))
            add(variable("QT1_full", "false", isActive = false))
            add(variable("QT2_full", "false", isActive = false))
            add(variable("answer_length", "Q1.value ? Q1.value.length : 0", returnType = "int"))
            if (withBrokenVariable) {
                add(variable("broken", "Q1.value == \"male\" && Q9.value == \"x\""))
            }
        })
        val quotaSkip = skip(
            skipTo,
            disqualify,
            "(Survey.var_QT1_met && Survey.var_QT1_full) || (Survey.var_QT2_met && Survey.var_QT2_full)"
        )
        put("groups", buildJsonArray {
            add(
                group(
                    "G1", "GROUP",
                    *listOfNotNull(
                        textQuestion("Q1", false, quotaSkip),
                        if (withRequired) textQuestion("Q1b", required = true) else null
                    ).toTypedArray()
                )
            )
            add(group("G2", "GROUP", textQuestion("Q2")))
            add(group("G3", "GROUP", textQuestion("Q3")))
            add(group("G4", "END"))
        })
    }
}
