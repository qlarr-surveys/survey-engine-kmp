package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.Instruction.Format
import com.qlarr.surveyengine.model.Instruction.SimpleState
import com.qlarr.surveyengine.model.Instruction.SkipInstruction
import com.qlarr.surveyengine.model.exposed.NavigationDirection
import com.qlarr.surveyengine.model.exposed.NavigationMode
import com.qlarr.surveyengine.model.exposed.ReturnType
import com.qlarr.surveyengine.model.exposed.SurveyMode
import com.qlarr.surveyengine.scriptengine.getNavigate
import com.qlarr.surveyengine.scriptengine.getValidate
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValueMetaTest {

    private val valueMeta = SimpleState("", ReservedCode.ValueMeta)
    private val q1ValueMetaReference = InstructionError.InvalidReference("Q1.value_meta", false)

    private fun Survey.question(code: String) = groups[0].questions.first { it.code == code }
    private fun Survey.instruction(questionCode: String, instructionCode: String) =
        question(questionCode).instructionList.first { it.code == instructionCode }

    @Test
    fun value_meta_reserved_code_round_trips() {
        assertTrue("value_meta".isReservedCode())
        assertEquals(ReservedCode.ValueMeta, "value_meta".toReservedCode())
        assertEquals(ReturnType.Map, ReservedCode.ValueMeta.defaultReturnType())
        assertFalse(ReservedCode.ValueMeta.defaultIsActive())
    }

    @Test
    fun value_meta_instruction_deserializes_with_defaults() {
        val instruction = jsonMapper.decodeFromString(
            Instruction.serializer(),
            """{"code":"value_meta"}"""
        ) as SimpleState
        assertEquals(ReservedCode.ValueMeta, instruction.reservedCode)
        assertEquals(ReturnType.Map, instruction.returnType)
        assertEquals("{}", instruction.text)
        assertFalse(instruction.isActive)
    }

    @Test
    fun inactive_map_value_meta_validates() {
        val survey = Question(
            "Q1",
            listOf(SimpleState("", ReservedCode.Value), valueMeta)
        ).wrapToSurvey()
        val output = ValidationUseCaseImpl(getValidate(), survey).validate(false)

        assertTrue(output.survey.instruction("Q1", "value_meta").noErrors())
        assertTrue(output.schema.none { it.componentCode == "Q1" && it.columnName.name.lowercase() == "value_meta" })
    }

    @Test
    fun active_value_meta_is_an_error() {
        val survey = Question(
            "Q1",
            listOf(SimpleState("{}", ReservedCode.ValueMeta, isActive = true))
        ).wrapToSurvey()
        val output = ValidationUseCaseImpl(getValidate(), survey).validate(false)

        assertEquals(
            listOf(InstructionError.InvalidValueMetaInstruction),
            output.survey.instruction("Q1", "value_meta").errors
        )
    }

    @Test
    fun masked_value_and_format_may_reference_value_meta() {
        val q1 = Question(
            "Q1",
            listOf(
                SimpleState("", ReservedCode.Value),
                valueMeta,
                SimpleState("QlarrScripts.safeAccess(Q1.value_meta, Survey.lang).toString()", ReservedCode.MaskedValue, isActive = true),
                Format("format_path", "QlarrScripts.safeAccess(Q1.value_meta, 'en')", lang = null)
            )
        )
        val q2 = Question(
            "Q2",
            listOf(
                SimpleState("QlarrScripts.safeAccess(Q1.value_meta, 'en').toString()", ReservedCode.MaskedValue, isActive = true),
                Format("format_path", "QlarrScripts.safeAccess(Q1.value_meta, 'de')", lang = null)
            )
        )
        val survey = Group("G1", questions = listOf(q1, q2)).wrapToSurvey()
        val output = ValidationUseCaseImpl(getValidate(), survey).validate(false)

        listOf("Q1", "Q2").forEach { code ->
            assertEquals(emptyList(), output.survey.instruction(code, "masked_value").errors)
            assertEquals(emptyList(), output.survey.instruction(code, "format_path").errors)
        }
    }

    @Test
    fun other_instructions_referencing_value_meta_are_errors() {
        val q1 = Question("Q1", listOf(SimpleState("", ReservedCode.Value), valueMeta))
        val q2 = Question(
            "Q2",
            listOf(
                SimpleState("Object.keys(Q1.value_meta).length > 0", ReservedCode.ConditionalRelevance, isActive = true),
                SimpleState("QlarrScripts.safeAccess(Q1.value_meta, 'en').toString()", ReservedCode.Value, isActive = true, returnType = ReturnType.String),
                SimpleState("Object.keys(Q1.value_meta).length > 0", ReservedCode.ValidationRule("validation_meta"), isActive = true),
                SkipInstruction(skipToComponent = "Q3", text = "Object.keys(Q1.value_meta).length > 0", isActive = true)
            )
        )
        val q3 = Question("Q3", listOf(SimpleState("", ReservedCode.Value)))
        val survey = Group("G1", questions = listOf(q1, q2, q3)).wrapToSurvey()
        val output = ValidationUseCaseImpl(getValidate(), survey).validate(false)

        listOf("conditional_relevance", "value", "validation_meta", "skip_to_Q3").forEach { code ->
            assertEquals(listOf(q1ValueMetaReference), output.survey.instruction("Q2", code).errors, code)
        }
    }

    @Test
    fun self_reference_to_value_meta_outside_masked_value_is_an_error() {
        val survey = Question(
            "Q1",
            listOf(
                SimpleState("", ReservedCode.Value),
                valueMeta,
                SimpleState("Object.keys(Q1.value_meta).length > 0", ReservedCode.ConditionalRelevance, isActive = true)
            )
        ).wrapToSurvey()
        val output = ValidationUseCaseImpl(getValidate(), survey).validate(false)

        assertEquals(listOf(q1ValueMetaReference), output.survey.instruction("Q1", "conditional_relevance").errors)
    }

    private fun navigationUseCase(survey: Survey, values: Map<String, JsonElement>): NavigationUseCaseImp {
        val validationOutput = ValidationUseCaseImpl(getValidate(), survey).validate(false)
        return NavigationUseCaseImp(
            validationOutput = validationOutput,
            surveyJson = jsonMapper.encodeToJsonElement(Survey.serializer(), validationOutput.survey).jsonObject,
            stringValues = values,
            navigationDirection = NavigationDirection.Start,
            navigationMode = NavigationMode.ALL_IN_ONE,
            lang = "en",
            skipInvalid = false,
            surveyMode = SurveyMode.ONLINE
        )
    }

    private fun navigate(
        values: Map<String, JsonElement>,
        maskedValue: String = "QlarrScripts.safeAccess(Q1.value_meta, Survey.lang).toString()"
    ): NavigationOutput {
        val survey = Question(
            "Q1",
            listOf(
                SimpleState("", ReservedCode.Value),
                valueMeta,
                SimpleState(maskedValue, ReservedCode.MaskedValue, isActive = true)
            )
        ).wrapToSurvey()
        return navigationUseCase(survey, values).navigate(getNavigate())
    }

    private fun navigationValues(values: Map<String, JsonElement>): Map<String, JsonElement> {
        val survey = Group(
            "G1",
            questions = listOf(
                Question("Q1", listOf(SimpleState("", ReservedCode.Value), valueMeta)),
                Question("Q2", listOf(SimpleState("", ReservedCode.Value), SimpleState("{}", ReservedCode.ValueMeta, isActive = true))),
                Question("Q3", listOf(SimpleState("", ReservedCode.Value))),
                Question("Q4", answers = listOf(Answer("A1", listOf(SimpleState("", ReservedCode.Value), valueMeta))))
            )
        ).wrapToSurvey()
        val script = Json.parseToJsonElement(navigationUseCase(survey, values).getNavigationScript()).jsonObject
        return script["values"]!!.jsonObject.mapValues { it.value.jsonObject["value"]!! }
    }

    private val meta = buildJsonObject { putJsonArray("en") { add("Germany"); add("Bavaria") } }

    @Test
    fun navigation_values_merge_schema_values_and_value_meta() {
        val values = navigationValues(
            mapOf(
                "Q1.value" to JsonPrimitive("BY"),
                "Q1.value_meta" to meta,
                "Q4A1.value" to JsonPrimitive("BE"),
                "Q4A1.value_meta" to meta
            )
        )

        assertEquals(JsonPrimitive("BY"), values["Q1.value"])
        assertEquals(meta, values["Q1.value_meta"])
        assertEquals(JsonPrimitive("BE"), values["Q4A1.value"])
        assertEquals(meta, values["Q4A1.value_meta"])
    }

    @Test
    fun navigation_values_drop_value_meta_that_is_not_an_object() {
        val values = navigationValues(
            mapOf(
                "Q1.value_meta" to JsonPrimitive("Germany"),
                "Q4A1.value_meta" to buildJsonArray { add("Germany") }
            )
        )

        assertFalse(values.containsKey("Q1.value_meta"))
        assertFalse(values.containsKey("Q4A1.value_meta"))
    }

    @Test
    fun navigation_values_drop_value_meta_without_a_valid_instruction() {
        val values = navigationValues(
            mapOf(
                "Q2.value_meta" to meta,
                "Q3.value_meta" to meta,
                "Q9.value_meta" to meta
            )
        )

        assertTrue(values.keys.none { it.endsWith(".value_meta") })
    }

    @Test
    fun navigation_values_still_drop_keys_outside_the_schema() {
        val values = navigationValues(
            mapOf(
                "Q1.value_meta" to meta,
                "Q1.relevance" to JsonPrimitive(false),
                "Q1.masked_value" to JsonPrimitive("x")
            )
        )

        assertEquals(meta, values["Q1.value_meta"])
        assertFalse(values.containsKey("Q1.relevance"))
        assertFalse(values.containsKey("Q1.masked_value"))
    }

    @Test
    fun value_meta_from_input_is_kept_in_state_and_saved() {
        val output = navigate(mapOf("Q1.value" to JsonPrimitive("BY"), "Q1.value_meta" to meta))

        assertEquals(meta, output.stateBindings[Dependency("Q1", ReservedCode.ValueMeta)])
        assertEquals(meta, output.toSave[Dependent("Q1", "value_meta")])
        assertEquals(JsonPrimitive("Germany,Bavaria"), output.stateBindings[Dependency("Q1", ReservedCode.MaskedValue)])
    }

    @Test
    fun non_object_value_meta_from_input_is_dropped() {
        val output = navigate(mapOf("Q1.value" to JsonPrimitive("BY"), "Q1.value_meta" to JsonPrimitive("Germany")))

        assertEquals(JsonObject(emptyMap()), output.stateBindings[Dependency("Q1", ReservedCode.ValueMeta)])
        assertEquals(JsonPrimitive(""), output.stateBindings[Dependency("Q1", ReservedCode.MaskedValue)])
    }

    @Test
    fun value_meta_input_is_ignored_for_components_without_the_instruction() {
        val output = navigate(
            mapOf("Q1.value" to JsonPrimitive("BY"), "Q2.value_meta" to buildJsonObject { put("en", "x") })
        )

        assertTrue(output.stateBindings.keys.none { it.componentCode == "Q2" })
    }

    @Test
    fun safe_access_returns_the_fallback_for_a_missing_key() {
        val output = navigate(
            mapOf("Q1.value" to JsonPrimitive("BY")),
            "QlarrScripts.safeAccess(Q1.value_meta, Survey.lang, 'none')"
        )

        assertEquals(JsonPrimitive("none"), output.stateBindings[Dependency("Q1", ReservedCode.MaskedValue)])
    }

    @Test
    fun safe_access_returns_a_falsy_fallback_for_a_missing_key() {
        val output = navigate(
            mapOf("Q1.value" to JsonPrimitive("BY")),
            "QlarrScripts.safeAccess(Q1.value_meta, Survey.lang, '').length.toString()"
        )

        assertEquals(JsonPrimitive("0"), output.stateBindings[Dependency("Q1", ReservedCode.MaskedValue)])
    }

    @Test
    fun safe_access_ignores_the_fallback_when_the_key_exists() {
        val output = navigate(
            mapOf("Q1.value" to JsonPrimitive("BY"), "Q1.value_meta" to meta),
            "QlarrScripts.safeAccess(Q1.value_meta, Survey.lang, 'none').toString()"
        )

        assertEquals(JsonPrimitive("Germany,Bavaria"), output.stateBindings[Dependency("Q1", ReservedCode.MaskedValue)])
    }
}
