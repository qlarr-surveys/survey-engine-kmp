package com.qlarr.surveyengine.context.assemble

import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.Instruction.*
import com.qlarr.surveyengine.model.exposed.ReturnType
import com.qlarr.surveyengine.scriptengine.getValidate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuotaTest {

    private fun quota(code: String, text: String = "true", errors: List<InstructionError> = listOf()) =
        SimpleState(text, ReservedCode.Quota(code), isActive = true, errors = errors)

    private fun SurveyComponent.instruction(code: String) = instructionList.firstOrNull { it.code == code }

    private fun SurveyComponent.child(vararg codes: String): SurveyComponent =
        codes.fold(this) { parent, code -> parent.children.first { it.code == code } }

    private fun survey(vararg quotas: Instruction) = Survey(
        instructionList = quotas.toList(),
        groups = listOf(
            Group(
                "G1",
                questions = listOf(
                    Question(
                        "Q1",
                        listOf(SimpleState("", ReservedCode.Value)),
                        answers = listOf(Answer("A1", listOf(SimpleState("", ReservedCode.Value))))
                    ),
                    Question("Q2", listOf(SimpleState("", ReservedCode.Value)))
                )
            ),
            Group("G2", questions = listOf(Question("Q3", listOf(SimpleState("", ReservedCode.Value))))),
            Group(
                "Gend",
                questions = listOf(Question("Qend", listOf(SimpleState("", ReservedCode.Value)))),
                groupType = GroupType.END
            )
        )
    )

    @Test
    fun addQuotaInputs_adds_inactive_is_quota_met_for_each_valid_quota() {
        val components = mutableListOf<SurveyComponent>(
            survey(quota("quota_a"), quota("quota_b", errors = listOf(InstructionError.QuotaNotOnSurvey)))
        )

        components.addQuotaInputs()

        assertEquals(
            SimpleState(
                "false",
                ReservedCode.IsQuotaMet("is_quota_met_a"),
                returnType = ReturnType.Boolean,
                isActive = false
            ),
            components[0].instruction("is_quota_met_a")
        )
        assertNull(components[0].instruction("is_quota_met_b"))
    }

    @Test
    fun addQuotaInputs_ignores_lists_not_starting_with_survey() {
        val components = mutableListOf<SurveyComponent>(Question("Q1"))

        components.addQuotaInputs()

        assertEquals(listOf<SurveyComponent>(Question("Q1")), components)
    }

    @Test
    fun addQuotaSkipInstructions_adds_disqualifying_skip_to_closest_question_or_group() {
        val components = mutableListOf<SurveyComponent>(survey(quota("quota_a")))
        val dependencyMap: DependencyMap = mapOf(
            Dependent("Survey", "quota_a") to listOf(
                Dependency("Q1A1", ReservedCode.Value),
                Dependency("Q1", ReservedCode.Value),
                Dependency("G2", ReservedCode.Relevance)
            )
        )

        components.addQuotaSkipInstructions(dependencyMap)

        val expected = SkipInstruction(
            skipToComponent = "Gend",
            disqualify = true,
            code = "skip_to_quota_a",
            text = "Survey.quota_a && Survey.is_quota_met_a"
        )
        val survey = components[0]
        assertEquals(expected, survey.child("G1", "Q1").instruction("skip_to_quota_a"))
        assertEquals(1, survey.child("G1", "Q1").instructionList.count { it.code == "skip_to_quota_a" })
        assertNull(survey.child("G1", "Q1", "A1").instruction("skip_to_quota_a"))
        assertEquals(expected, survey.child("G2").instruction("skip_to_quota_a"))
        assertNull(survey.child("G1").instruction("skip_to_quota_a"))
        assertNull(survey.child("G1", "Q2").instruction("skip_to_quota_a"))
        assertNull(survey.child("G2", "Q3").instruction("skip_to_quota_a"))
    }

    @Test
    fun addQuotaSkipInstructions_ignores_survey_and_end_group_dependencies() {
        val original = survey(quota("quota_a"))
        val components = mutableListOf<SurveyComponent>(original)
        val dependencyMap: DependencyMap = mapOf(
            Dependent("Survey", "quota_a") to listOf(
                Dependency("Survey", ReservedCode.Mode),
                Dependency("Gend", ReservedCode.Relevance),
                Dependency("Qend", ReservedCode.Value)
            )
        )

        components.addQuotaSkipInstructions(dependencyMap)

        assertEquals(original, components[0])
    }

    @Test
    fun addQuotaSkipInstructions_ignores_faulty_quotas() {
        val original = survey(quota("quota_a", errors = listOf(InstructionError.QuotaNotOnSurvey)))
        val components = mutableListOf<SurveyComponent>(original)
        val dependencyMap: DependencyMap = mapOf(
            Dependent("Survey", "quota_a") to listOf(Dependency("Q2", ReservedCode.Value))
        )

        components.addQuotaSkipInstructions(dependencyMap)

        assertEquals(original, components[0])
    }

    @Test
    fun addQuotaSkipInstructions_does_nothing_without_end_group() {
        val original = Survey(
            instructionList = listOf(quota("quota_a")),
            groups = listOf(Group("G1", questions = listOf(Question("Q1", listOf(SimpleState("", ReservedCode.Value))))))
        )
        val components = mutableListOf<SurveyComponent>(original)
        val dependencyMap: DependencyMap = mapOf(
            Dependent("Survey", "quota_a") to listOf(Dependency("Q1", ReservedCode.Value))
        )

        components.addQuotaSkipInstructions(dependencyMap)

        assertEquals(original, components[0])
    }

    @Test
    fun addQuotaSkipInstructions_adds_one_skip_per_quota() {
        val components = mutableListOf<SurveyComponent>(survey(quota("quota_a"), quota("quota_b")))
        val dependencyMap: DependencyMap = mapOf(
            Dependent("Survey", "quota_a") to listOf(Dependency("Q2", ReservedCode.Value)),
            Dependent("Survey", "quota_b") to listOf(Dependency("Q2", ReservedCode.Value))
        )

        components.addQuotaSkipInstructions(dependencyMap)

        val q2 = components[0].child("G1", "Q2")
        assertEquals("Survey.quota_a && Survey.is_quota_met_a", (q2.instruction("skip_to_quota_a") as SkipInstruction).text)
        assertEquals("Survey.quota_b && Survey.is_quota_met_b", (q2.instruction("skip_to_quota_b") as SkipInstruction).text)
    }

    @Test
    fun validate_adds_quota_inputs_skips_and_disqualify() {
        val contextBuilder = ContextBuilder(mutableListOf(survey(quota("quota_a", "Q1A1.value > 3"))), getValidate())

        contextBuilder.validate()

        val survey = contextBuilder.components[0]
        assertTrue(survey.instruction("quota_a")!!.errors.isEmpty())
        assertEquals(false, (survey.instruction("is_quota_met_a") as SimpleState).isActive)
        assertEquals(
            "Survey.quota_a && Survey.is_quota_met_a",
            (survey.child("G1", "Q1").instruction("skip_to_quota_a") as SkipInstruction).text
        )
        assertNull(survey.child("G1", "Q2").instruction("skip_to_quota_a"))
        assertEquals(
            "Survey.quota_a && Survey.is_quota_met_a",
            (survey.instruction("disqualified") as SimpleState).text
        )
    }

    @Test
    fun validate_skips_quota_with_script_errors() {
        val contextBuilder = ContextBuilder(mutableListOf(survey(quota("quota_a", "Q1.value >"))), getValidate())

        contextBuilder.validate()

        val survey = contextBuilder.components[0]
        assertTrue(survey.instruction("quota_a")!!.errors.isNotEmpty())
        assertNull(survey.instruction("is_quota_met_a"))
        assertNull(survey.child("G1", "Q1").instruction("skip_to_quota_a"))
    }

    @Test
    fun revalidating_without_the_quota_drops_its_old_skips() {
        val firstRun = ContextBuilder(mutableListOf(survey(quota("quota_a", "Q1A1.value > 3"))), getValidate())
        firstRun.validate()
        val withoutQuota = (firstRun.components[0] as Survey).let { survey ->
            survey.duplicate(instructionList = survey.instructionList.filterNot { it.code == "quota_a" })
        }

        val secondRun = ContextBuilder(mutableListOf(withoutQuota), getValidate())
        secondRun.validate()

        assertNull(secondRun.components[0].child("G1", "Q1").instruction("skip_to_quota_a"))
        assertNull(secondRun.components[0].instruction("is_quota_met_a"))
        assertEquals("false", (secondRun.components[0].instruction("disqualified") as SimpleState).text)
    }
}
