package com.qlarr.surveyengine.validation

import com.qlarr.surveyengine.model.ReservedCode
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.Instruction.*
import kotlin.test.assertEquals
import kotlin.test.Test


class ComponentValidatorTest {


    @Test
    fun validates_special_type_groups() {
        val survey_ok = Survey(
            groups = listOf(
                Group("G2"),
                Group("G3"),
                Group("G4"),
                Group("G5", groupType = GroupType.END, questions = listOf(Question("Q1", answers = listOf(Answer("A1", instructionList = listOf(
                    SimpleState("", ReservedCode.Value)
                )))))),
            )
        )
        val survey_not_ok = Survey(
            groups = listOf(
                Group("G1"),
                Group("G3"),
                Group("G4", groupType = GroupType.END),
                Group("G5"),
            )
        )
        val validated_ok = listOf(survey_ok).validateSpecialTypeGroups()[0]
        assertEquals(
            emptyList<ComponentError>(),
            validated_ok.children.map { it.errors }.flatten()
        )
        assertEquals(
            InstructionError.InvalidInstructionInEndGroup,
            validated_ok.children[3].children[0].children[0].instructionList[0].errors[0]
        )
        val validated_not_ok = listOf(survey_not_ok).validateSpecialTypeGroups()[0]
        assertEquals(ComponentError.MISPLACED_END_GROUP, validated_not_ok.children[2].errors[0])

    }

    @Test
    fun Random_Group_Equations_are_validated() {
        val QUESTION_ONE = Question(
            "Q1",
            instructionList = listOf(
                RandomGroups(
                    groups = listOf(
                        listOf("A1", "A2"),
                        listOf("A4", "A5"),
                        listOf("A4", "A8", "A9")
                    )
                )
            ),
            answers = listOf(
                Answer("A1"),
                Answer("A2"),
                Answer("A3"),
                Answer("A4"),
                Answer("A5"),
                Answer("A6")
            )
        )

        val components = listOf(QUESTION_ONE)
        val validated = components.map { it.validateInstructions() }
        assertEquals(InstructionError.DuplicateRandomGroupItems(listOf("A4")), validated[0].instructionList[0].errors[0])
        assertEquals(
            InstructionError.RandomGroupItemNotChild(listOf("A8", "A9")),
            validated[0].instructionList[0].errors[1]
        )
    }

    @Test
    fun priority_and_random_groups_validates_against_welcome_and_end_groups() {
        val SURVEY = Survey(
            instructionList = listOf(
                PriorityGroups(
                    priorities = listOf(
                        PriorityGroup(weights = listOf(ChildPriority("G3"), ChildPriority("G4"))),
                        PriorityGroup(weights = listOf(ChildPriority("G5"), ChildPriority("G6"), ChildPriority("G7")))
                    )
                ),
                RandomGroups(groups = listOf(
                    listOf("G3", "G4"),
                    listOf("G5", "G6", "G7")
                ))
            ),
            groups = listOf(
                Group("G2"),
                Group("G3"),
                Group("G4"),
                Group("G5"),
                Group("G6"),
                Group("G7", groupType = GroupType.END)
            )
        )

        val components = listOf(SURVEY)
        val validated = components.map { it.validateInstructions() }
        assertEquals(InstructionError.InvalidRandomItem    (listOf("G7")), validated[0].instructionList[1].errors[0])
    }
    @Test
    fun Priority_Group_Equations_are_validated() {
        val QUESTION_ONE = Question(
            "Q1",
            instructionList = listOf(
                PriorityGroups(
                    priorities = listOf(
                        PriorityGroup(weights = listOf(ChildPriority("A1"), ChildPriority("A2"))),
                        PriorityGroup(weights = listOf(ChildPriority("A4"), ChildPriority("A5"))),
                        PriorityGroup(weights = listOf(ChildPriority("A4"), ChildPriority("A8"), ChildPriority("A9")))
                    )
                )
            ),
            answers = listOf(
                Answer("A1"),
                Answer("A2"),
                Answer("A3"),
                Answer("A4"),
                Answer("A5"),
                Answer("A6")
            )
        )

        val components = listOf(QUESTION_ONE)
        val validated = components.map { it.validateInstructions() }
        assertEquals(InstructionError.DuplicatePriorityGroupItems(listOf("A4")), validated[0].instructionList[0].errors[0])
        assertEquals(
            InstructionError.PriorityGroupItemNotChild(listOf("A8", "A9")),
            validated[0].instructionList[0].errors[1]
        )
    }

    @Test
    fun Parent_conditional_relevance_Equations_are_validated() {
        val QUESTION_ONE = Question(
            "Q1",
            instructionList = listOf(
                ParentRelevance(children = listOf(listOf("A1", "A2", "A4", "A5", "A9")))
            ),
            answers = listOf(
                Answer("A1"),
                Answer("A2"),
                Answer("A3"),
                Answer("A4"),
                Answer("A5"),
                Answer("A6")
            )
        )

        val components = listOf(QUESTION_ONE)
        val validated = components.map { it.validateInstructions() }
        assertEquals(InstructionError.InvalidChildReferences(listOf("A9")), validated[0].instructionList[0].errors[0])
    }
}
