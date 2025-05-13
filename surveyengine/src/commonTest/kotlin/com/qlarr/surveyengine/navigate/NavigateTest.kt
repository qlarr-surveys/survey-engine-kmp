@file:Suppress("UNCHECKED_CAST")

package com.qlarr.surveyengine.navigate

import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.ReservedCode.Order
import com.qlarr.surveyengine.model.ReservedCode.Relevance
import com.qlarr.surveyengine.model.exposed.NavigationDirection
import com.qlarr.surveyengine.model.exposed.NavigationIndex
import com.qlarr.surveyengine.model.exposed.NavigationMode
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigateTest {
    private val survey = Survey(
        groups = listOf(
            Group(
                code = "G1",
                instructionList = listOf(Instruction.SimpleState("", Relevance)),
                questions = listOf(
                    Question(code = "Q1"),
                    Question(code = "Q2"),
                    Question(code = "Q3", instructionList = listOf(Instruction.SimpleState("", Relevance))),
                    Question(code = "Q4")
                )
            ),
            Group(
                code = "G2",
                questions = listOf(
                    Question(code = "Q5", instructionList = listOf(Instruction.SimpleState("", Relevance))),
                    Question(code = "Q6"),
                    Question(code = "Q7"),
                    Question(code = "Q8"),
                )
            ),
            Group(
                code = "G3",
                questions = listOf(
                    Question(code = "Q9")
                )
            ),
            Group(
                code = "G4",
                questions = listOf(
                    Question(code = "Q10"),
                    Question(code = "Q11"),
                    Question(code = "Q12", instructionList = listOf(Instruction.SimpleState("", Relevance))),
                    Question(code = "Q13", instructionList = listOf(Instruction.SimpleState("", Relevance)))
                )
            ),
            Group(
                code = "G5", instructionList = listOf(Instruction.SimpleState("", Relevance)),
                questions = listOf(
                    Question(code = "Q14"),
                    Question(code = "Q15", instructionList = listOf(Instruction.SimpleState("", Relevance))),
                    Question(code = "Q16"),
                    Question(code = "Q17")
                )
            )
        )
    )


    @Test
    fun with_start_and_all_in_one_all_groups_are_picked() {
        assertEquals(
            NavigationIndex.Groups(
                listOf("G1", "G2", "G3", "G4", "G5")
            ), survey.navigate(
                navigationIndex = null,
                navigationDirection = NavigationDirection.Start,
                navigationMode = NavigationMode.ALL_IN_ONE,
                mapOf(Dependency("Survey", ReservedCode.Validity) to true)
            )
        )
    }

    @Test
    fun with_start_and_group_by_group_first_relevant_group_is_picked() {
        assertEquals(
            NavigationIndex.Group("G2"), survey.navigate(
                navigationIndex = null,
                navigationDirection = NavigationDirection.Start,
                navigationMode = NavigationMode.GROUP_BY_GROUP,
                mapOf(
                    Dependency("Survey", ReservedCode.Validity) to true,
                    Dependency("G1", Relevance) to false
                )
            )
        )
    }

    @Test
    fun with_start_and_question_by_question_first_relevant_question_is_picked() {
        assertEquals(
            NavigationIndex.Question("Q6"), survey.navigate(
                navigationIndex = null,
                navigationDirection = NavigationDirection.Start,
                navigationMode = NavigationMode.QUESTION_BY_QUESTION,
                mapOf(
                    Dependency("Survey", ReservedCode.Validity) to true,
                    Dependency("G1", Relevance) to false,
                    Dependency("Q5", Relevance) to false
                )
            )
        )
    }

    @Test
    fun with_next_and_question_by_question_first_relevant_question_is_picked() {
        assertEquals(
            NavigationIndex.Question("Q8"), survey.navigate(
                navigationIndex = NavigationIndex.Question("Q6"),
                navigationDirection = NavigationDirection.Next,
                navigationMode = NavigationMode.QUESTION_BY_QUESTION,
                mapOf(
                    Dependency("Q7", Relevance) to false,
                    Dependency("Survey", ReservedCode.Validity) to true
                )
            )
        )
    }

    @Test
    fun with_next_and_group_by_group_first_relevant_group_is_picked() {
        assertEquals(
            NavigationIndex.Group("G4"), survey.navigate(
                navigationIndex = NavigationIndex.Group("G2"),
                navigationDirection = NavigationDirection.Next,
                navigationMode = NavigationMode.GROUP_BY_GROUP,
                mapOf(

                    Dependency("G3", Relevance) to false,
                    Dependency("Survey", ReservedCode.Validity) to true
                )
            )
        )
    }

    @Test
    fun with_next_and_question_by_question_first_relevant_question_is_picked_skipping_questions() {
        assertEquals(
            NavigationIndex.Question("Q11"), survey.navigate(
                navigationIndex = NavigationIndex.Question("Q6"),
                navigationDirection = NavigationDirection.Next,
                navigationMode = NavigationMode.QUESTION_BY_QUESTION,
                mapOf(
                    Dependency("Q7", Relevance) to false,
                    Dependency("Q8", Relevance) to false,
                    Dependency("Q10", Relevance) to false,
                    Dependency("G3", Relevance) to false,
                    Dependency("Survey", ReservedCode.Validity) to true
                )
            )
        )
    }

    @Test
    fun with_next_and_questiodn_by_question_first_relevant_question_is_picked_skipping_questions() {
        assertEquals(
            NavigationIndex.Question("Q11"), survey.navigate(
                navigationIndex = NavigationIndex.Question("Q6"),
                navigationDirection = NavigationDirection.Next,
                navigationMode = NavigationMode.QUESTION_BY_QUESTION,
                mapOf(
                    Dependency("Q7", Relevance) to false,
                    Dependency("Q8", Relevance) to false,
                    Dependency("Q10", Relevance) to false,
                    Dependency("Q9", Relevance) to false,
                    Dependency("Survey", ReservedCode.Validity) to true
                )
            )
        )
    }

    @Test
    fun with_prev_and_question_by_question_last_relevant_question_is_picked() {
        assertEquals(
            NavigationIndex.Question("Q6"), survey.navigate(
                navigationIndex = NavigationIndex.Question("Q8"),
                navigationDirection = NavigationDirection.Previous,
                navigationMode = NavigationMode.QUESTION_BY_QUESTION,
                mapOf(
                    Dependency("Survey", ReservedCode.Validity) to true,
                    Dependency("Q7", Relevance) to false
                )
            )
        )
    }

    @Test
    fun with_prev_and_group_by_group_first_relevant_group_is_picked() {
        assertEquals(
            NavigationIndex.Group("G2"), survey.navigate(
                navigationIndex = NavigationIndex.Group("G4"),
                navigationDirection = NavigationDirection.Previous,
                navigationMode = NavigationMode.GROUP_BY_GROUP,
                mapOf(
                    Dependency("Survey", ReservedCode.Validity) to true,
                    Dependency("G3", Relevance) to false
                )
            )
        )
    }

    @Test
    fun with_prev_and_question_by_question_first_relevant_question_is_picked_skipping_questions() {
        assertEquals(
            NavigationIndex.Question("Q6"), survey.navigate(

                navigationIndex = NavigationIndex.Question("Q14"),
                navigationDirection = NavigationDirection.Previous,
                navigationMode = NavigationMode.QUESTION_BY_QUESTION,
                mapOf(
                    Dependency("Survey", ReservedCode.Validity) to true,
                    Dependency("Q7", Relevance) to false,
                    Dependency("Q8", Relevance) to false,
                    Dependency("Q13", Relevance) to false,
                    Dependency("G3", Relevance) to false,
                    Dependency("G4", Relevance) to false
                )
            )
        )
    }

    @Test
    fun with_prev_and_question_by_question_first_relevant_question_is_dpicked_skipping_questions() {
        assertEquals(
            NavigationIndex.Question("Q6"), survey.navigate(
                navigationIndex = NavigationIndex.Question("Q14"),
                navigationDirection = NavigationDirection.Previous,
                navigationMode = NavigationMode.QUESTION_BY_QUESTION,
                mapOf(
                    Dependency("Survey", ReservedCode.Validity) to true,
                    Dependency("Q7", Relevance) to false,
                    Dependency("Q8", Relevance) to false,
                    Dependency("Q13", Relevance) to false,
                    Dependency("Q9", Relevance) to false,
                    Dependency("G4", Relevance) to false
                )
            )
        )
    }

    @Test
    fun sortByOrder() {
        val question = Question(
            code = "Q1",
            answers = listOf(
                Answer("A1"),
                Answer("A2"),
                Answer("A3"),
                Answer("A4")

            )
        )
        val bindings = mapOf(
            Dependency("Q1A1", Order) to 3,
            Dependency("Q1A2", Order) to 2,
            Dependency("Q1A3", Order) to 0,
            Dependency("Q1A4", Order) to 1
        )
        val list = mutableListOf(question)
        (list as MutableList<SurveyComponent>).sortByOrder("", bindings)
        assertEquals(listOf("A3", "A4", "A2", "A1"), list[0].children.map { it.code })
    }
}