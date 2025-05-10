package com.qlarr.surveyengine.context.assemble

import com.qlarr.surveyengine.model.ReservedCode
import com.qlarr.surveyengine.model.*
import kotlin.test.assertEquals
import kotlin.test.Test

@Suppress("UNCHECKED_CAST")
class AddInstructionsKtTest {

    @Test
    fun addParentRelevanceInstruction() {
        val QUESTION_ONE = Question(
            "Q1",
            instructionList = listOf(
                Instruction.ParentRelevance(children = listOf(listOf("A1", "A2"), listOf("A4", "A5", "A6"))),
                Instruction.SimpleState("1 > 0", ReservedCode.ConditionalRelevance)
            ),
            answers = listOf(
                Answer("A1", instructionList = listOf(Instruction.SimpleState("false", ReservedCode.ConditionalRelevance))),
                Answer("A2", instructionList = listOf(Instruction.SimpleState("false", ReservedCode.ConditionalRelevance))),
                Answer("A3"),
                Answer("A4", instructionList = listOf(Instruction.SimpleState("false", ReservedCode.ConditionalRelevance))),
                Answer("A5", instructionList = listOf(Instruction.SimpleState("false", ReservedCode.ConditionalRelevance))),
                Answer("A6", instructionList = listOf(Instruction.SimpleState("false", ReservedCode.ConditionalRelevance)))
            )
        )
        val QUESTION_TWO = Question(
            "Q2",
            instructionList = listOf(
                Instruction.ParentRelevance(children = listOf(listOf("A1", "A2", "A3"), listOf("A4", "A5", "A6")))
            ),
            answers = listOf(
                Answer("A1", instructionList = listOf(Instruction.SimpleState("false", ReservedCode.ConditionalRelevance))),
                Answer("A2", instructionList = listOf(Instruction.SimpleState("false", ReservedCode.ConditionalRelevance))),
                Answer("A4", instructionList = listOf(Instruction.SimpleState("false", ReservedCode.ConditionalRelevance))),
                Answer("A5", instructionList = listOf(Instruction.SimpleState("false", ReservedCode.ConditionalRelevance))),
                Answer("A6", instructionList = listOf(Instruction.SimpleState("false", ReservedCode.ConditionalRelevance)))
            )
        )
        val group = Group(
            "G1",
            questions = listOf(QUESTION_ONE, QUESTION_TWO),
            instructionList = listOf(
                Instruction.SimpleState("1 > 0", ReservedCode.ConditionalRelevance)
            )
        )

        val validatedGroup =
            (mutableListOf(group) as MutableList<SurveyComponent>).apply { addParentRelevanceInstruction() }[0]
        val validatedQuestion = validatedGroup.children[0]
        val validatedQuestion2 = validatedGroup.children[1]
        assertEquals(
            "(Q1.conditional_relevance && Q1.children_relevance) || Q2.children_relevance",
            (validatedGroup.instructionList[1] as Instruction.SimpleState).text
        )
        assertEquals(
            "Q1A1.conditional_relevance || Q1A2.conditional_relevance || Q1A4.conditional_relevance || Q1A5.conditional_relevance || Q1A6.conditional_relevance",
            (validatedQuestion.instructionList[2] as Instruction.SimpleState).text
        )
        assertEquals(
            "Q2A1.conditional_relevance || Q2A2.conditional_relevance || Q2A4.conditional_relevance || Q2A5.conditional_relevance || Q2A6.conditional_relevance",
            (validatedQuestion2.instructionList[1] as Instruction.SimpleState).text
        )
    }

    @Test
    fun addParentRelevanceInstruction2() {
        val QUESTION_ONE = Question(
            "Q1",
            instructionList = listOf(
                Instruction.ParentRelevance(
                    children = listOf(
                        listOf("A1", "A2", "A3"),
                        listOf("A4", "A5", "A6"),
                        listOf("A7", "A8", "A9")
                    )
                )
            ),
            answers = listOf(
                Answer("A1", instructionList = listOf(Instruction.SimpleState("true", ReservedCode.ConditionalRelevance))),
                Answer("A2"),
                Answer("A3", instructionList = listOf(Instruction.SimpleState("true", ReservedCode.ConditionalRelevance))),
                Answer(
                    "A4",
                    instructionList = listOf(
                        Instruction.SimpleState(
                            "true",
                            ReservedCode.ConditionalRelevance,
                            isActive = false
                        )
                    )
                ),
                Answer("A5", instructionList = listOf(Instruction.SimpleState("true", ReservedCode.ConditionalRelevance))),
                Answer("A6", instructionList = listOf(Instruction.SimpleState("true", ReservedCode.ConditionalRelevance))),
                Answer("A7", instructionList = listOf(Instruction.SimpleState("false", ReservedCode.ConditionalRelevance))),
                Answer("A8", instructionList = listOf(Instruction.SimpleState("false", ReservedCode.ConditionalRelevance))),
                Answer("A9", instructionList = listOf(Instruction.SimpleState("false", ReservedCode.ConditionalRelevance)))
            )
        )

        val validatedQuestion =
            (mutableListOf(QUESTION_ONE) as MutableList<SurveyComponent>).apply { addParentRelevanceInstruction() }[0]
        assertEquals(
            "Q1A7.conditional_relevance || Q1A8.conditional_relevance || Q1A9.conditional_relevance",
            (validatedQuestion.instructionList[1] as Instruction.SimpleState).text
        )
    }

}