package com.qlarr.surveyengine.context.assemble

import com.qlarr.surveyengine.model.ReservedCode
import com.qlarr.surveyengine.dependency.componentIndices
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.Instruction.RandomGroups
import com.qlarr.surveyengine.model.Instruction.SkipInstruction
import kotlin.test.assertEquals
import kotlin.test.Test

class SkipCodesTest {

    @Test
    fun test1() {
        val skipToQ3 = SkipInstruction("Q3")
        val skipToG1 = SkipInstruction("G1", toEnd = true)
        val skipToG5 = SkipInstruction("G5")
        val survey = Survey(
            groups = listOf(
                Group(
                    "G1",
                    questions = listOf(
                        Question("Q1"),
                        Question("Q2"),
                        Question("Q3"),
                    )
                ),
                Group("G2"),
                Group("G3"),
                Group("G4"),
                Group("G5")
            ),
            instructionList = listOf(RandomGroups(listOf(listOf("G2", "G3", "G4"))))
        )
        val componentIndices = listOf(survey).componentIndices()
        assertEquals(
            skipCodes("Q1", skipToQ3.copy(toEnd = true), componentIndices),
            skipCodes("Q1", skipToQ3, componentIndices)
        )
        assertEquals(
            listOf(SkipCodeOutput("Q2", false, false, "", "")),
            skipCodes("Q1", skipToQ3, componentIndices)
        )


        assertEquals(
            listOf(SkipCodeOutput("Q3", fromOrderNecessary = false, toOrderNecessary = false, "", "")),
            skipCodes("Q2", skipToG1, componentIndices)
        )


        assertEquals(
            listOf(
                SkipCodeOutput("G2", true, false, "G4", ""),
                SkipCodeOutput("G3", true, false, "G4", "")
            ), skipCodes("G4", skipToG5, componentIndices)
        )


    }

    @Test
    fun test2() {
        val survey = Survey(
            groups = listOf(
                Group(
                    "G1",
                    questions = listOf(
                        Question("Q1"),
                        Question("Q2"),
                        Question("Q3"),
                    ),
                    instructionList = listOf(RandomGroups(listOf(listOf("Q1", "Q2"))))
                ),
                Group("G2"),
                Group("G3"),
                Group("G4"),
                Group(
                    "G5",
                    questions = listOf(
                        Question("Q4"),
                        Question("Q5"),
                        Question("Q6"),
                    ),
                    instructionList = listOf(RandomGroups(listOf(listOf("Q5", "Q6"))))
                )
            ),
            instructionList = listOf(RandomGroups(listOf(listOf("G2", "G1"), listOf("G3", "G5"))))
        )
        val componentIndices = listOf(survey).componentIndices()



        assertEquals(
            listOf(
                SkipCodeOutput("G2", true, false, "G1", ""),
                SkipCodeOutput("G3", false, true, "", "G5"),
                SkipCodeOutput("G4", false, true, "", "G5"),
                SkipCodeOutput("Q1", true, false, "Q2", ""),
                SkipCodeOutput("Q3", false, false, "", ""),
                SkipCodeOutput("Q4", false, false, "", ""),
                SkipCodeOutput("Q5", false, true, "", "Q6"),
            ), skipCodes("Q2", SkipInstruction("Q6"), componentIndices)
        )

        assertEquals(
            listOf(
                SkipCodeOutput("G1", true, false, "G2", ""),
                SkipCodeOutput("G3", false, true, "", "G5"),
                SkipCodeOutput("G4", false, true, "", "G5"),
                SkipCodeOutput("Q4", false, false, "", ""),
                SkipCodeOutput("Q5", false, true, "", "Q6"),
            ), skipCodes("G2", SkipInstruction("Q6"), componentIndices)
        )
    }


    @Test
    fun test3() {
        val survey = Survey(
            groups = listOf(
                Group(
                    "G1",
                    questions = listOf(
                        Question("Q1"),
                        Question(
                            "Q2", instructionList = listOf(
                                Instruction.SimpleState(text = "", reservedCode = ReservedCode.Value),
                                SkipInstruction(skipToComponent = "G4", isActive = true, text = "Q2.value=='A1'")
                            )
                        ),
                        Question(
                            "Q3", instructionList = listOf(
                                SkipInstruction(skipToComponent = "G2", isActive = true, text = "Q3.value=='A2'"),
                                SkipInstruction(skipToComponent = "G3", isActive = true, text = "Q3.value=='A3'"),
                                SkipInstruction(skipToComponent = "G4", isActive = true, text = "Q3.value=='A4'")
                            )
                        ),
                        Question("Q4"),
                    )
                ),
                Group(
                    "G2",
                    instructionList = listOf(
                        SkipInstruction(
                            skipToComponent = "G5",
                            isActive = true,
                            text = "Q3.relevance"
                        )
                    )
                ),
                Group(
                    "G3",
                    instructionList = listOf(
                        SkipInstruction(
                            skipToComponent = "G5",
                            isActive = true,
                            text = "Q3.relevance"
                        )
                    )
                ),
                Group(
                    "G4",
                    instructionList = listOf(
                        SkipInstruction(
                            skipToComponent = "G5",
                            isActive = true,
                            text = "Q3.relevance"
                        )
                    )
                ),
                Group("G5")
            ),
            instructionList = listOf(RandomGroups(listOf(listOf("G2", "G3", "G4"))))

        )
        val componentIndices = listOf(survey).componentIndices()
        val newSurvey = mutableListOf(survey as SurveyComponent).apply { addNotSkippedInstructions(componentIndices) }[0] as Survey
        assertEquals(
            "(G4.order > G2.order || !Q3.skip_to_G2 || !Q3.relevance) && (G4.order > G3.order || !Q3.skip_to_G3 || !Q3.relevance) && (G4.order < G2.order || !G2.skip_to_G5 || !G2.relevance) && (G4.order < G3.order || !G3.skip_to_G5 || !G3.relevance)",
            (newSurvey.groups[3].instructionList[1] as Instruction.SimpleState).text
        )

    }

}