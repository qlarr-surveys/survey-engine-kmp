package com.qlarr.surveyengine.context.assemble

import com.qlarr.surveyengine.model.Answer
import com.qlarr.surveyengine.model.Group
import com.qlarr.surveyengine.model.Question
import com.qlarr.surveyengine.model.RepeatInfo
import com.qlarr.surveyengine.model.Survey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExpandRepeatablesTest {

    private fun repeatable(vararg tokens: String) = RepeatInfo.Repeatable(
        range = tokens.toList(),
        relevanceInstruction = "Qbrands.value.includes('{{token}}')"
    )

    @Test
    fun keeps_repeatable_group_and_adds_expanded_copies() {
        val survey = Survey(
            groups = listOf(
                Group(
                    "G1", repeatInfo = repeatable("a", "b"),
                    questions = listOf(
                        Question("Q1", answers = listOf(Answer("A1"))),
                        Question("Q2")
                    )
                )
            )
        )

        val expanded = listOf(survey).expandRepeatables()[0] as Survey

        assertEquals(listOf("G1", "G1_a", "G1_b"), expanded.groups.map { it.code })

        val template = expanded.groups[0]
        assertEquals(repeatable("a", "b"), template.repeatInfo)
        assertEquals(listOf("Q1", "Q2"), template.questions.map { it.code })

        val g1a = expanded.groups[1]
        assertEquals(RepeatInfo.Repeated("a"), g1a.repeatInfo)
        assertEquals(listOf("Q1_a", "Q2_a"), g1a.questions.map { it.code })
        assertNull(g1a.questions[0].repeatInfo)
        assertEquals(listOf("A1"), g1a.questions[0].answers.map { it.code })

        val g1b = expanded.groups[2]
        assertEquals(RepeatInfo.Repeated("b"), g1b.repeatInfo)
        assertEquals(listOf("Q1_b", "Q2_b"), g1b.questions.map { it.code })
    }

    @Test
    fun keeps_repeatable_question_and_adds_expanded_copies() {
        val survey = Survey(
            groups = listOf(
                Group(
                    "G1",
                    questions = listOf(
                        Question("Q1", repeatInfo = repeatable("a", "b"), answers = listOf(Answer("A1"), Answer("A2")))
                    )
                )
            )
        )

        val group = (listOf(survey).expandRepeatables()[0] as Survey).groups[0]

        assertEquals("G1", group.code)
        assertEquals(listOf("Q1", "Q1_a", "Q1_b"), group.questions.map { it.code })
        assertEquals(repeatable("a", "b"), group.questions[0].repeatInfo)
        assertEquals(RepeatInfo.Repeated("a"), group.questions[1].repeatInfo)
        assertEquals(listOf("A1", "A2"), group.questions[1].answers.map { it.code })
    }

    @Test
    fun keeps_repeatable_answer_containing_answers_and_adds_copies() {
        val survey = Survey(
            groups = listOf(
                Group(
                    "G1",
                    questions = listOf(
                        Question(
                            "Q1",
                            answers = listOf(
                                Answer(
                                    "A1", repeatInfo = repeatable("a", "b"),
                                    answers = listOf(Answer("A2"), Answer("A3"))
                                )
                            )
                        )
                    )
                )
            )
        )

        val question = (listOf(survey).expandRepeatables()[0] as Survey).groups[0].questions[0]

        assertEquals(listOf("A1", "A1_a", "A1_b"), question.answers.map { it.code })
        assertEquals(RepeatInfo.Repeated("a"), question.answers[1].repeatInfo)
        assertEquals(listOf("A2", "A3"), question.answers[1].answers.map { it.code })
    }

    @Test
    fun keeps_repeatable_leaf_answer_and_adds_copies() {
        val survey = Survey(
            groups = listOf(
                Group(
                    "G1",
                    questions = listOf(
                        Question("Q1", answers = listOf(Answer("A1", repeatInfo = repeatable("a", "b"))))
                    )
                )
            )
        )

        val question = (listOf(survey).expandRepeatables()[0] as Survey).groups[0].questions[0]

        assertEquals(listOf("A1", "A1_a", "A1_b"), question.answers.map { it.code })
        assertEquals(
            listOf(repeatable("a", "b"), RepeatInfo.Repeated("a"), RepeatInfo.Repeated("b")),
            question.answers.map { it.repeatInfo }
        )
    }

    @Test
    fun expansion_is_idempotent() {
        val survey = Survey(
            groups = listOf(
                Group("G1", repeatInfo = repeatable("a", "b"), questions = listOf(Question("Q1")))
            )
        )

        val once = listOf(survey).expandRepeatables()
        val twice = once.expandRepeatables()

        assertEquals(once, twice)
    }

    @Test
    fun drops_stale_expanded_before_regenerating() {
        val survey = Survey(
            groups = listOf(
                Group("G1", repeatInfo = repeatable("a", "b"), questions = listOf(Question("Q1"))),
                Group("G1_old", repeatInfo = RepeatInfo.Repeated("old"), questions = listOf(Question("Q1_old")))
            )
        )

        val expanded = listOf(survey).expandRepeatables()[0] as Survey

        assertEquals(listOf("G1", "G1_a", "G1_b"), expanded.groups.map { it.code })
    }
}
