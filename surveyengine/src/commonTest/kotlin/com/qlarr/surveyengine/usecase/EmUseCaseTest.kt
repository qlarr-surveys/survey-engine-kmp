package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.common.buildScriptEngine
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.Instruction.SimpleState
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertTrue

class EmUseCaseTest {

    @Test
    fun script_errors_are_reflected() {
        val questionComponent = Question(
            code = "Q1",
            instructionList = listOf(SimpleState(";;;getdaSD dasd", ReservedCode.Value, isActive = true))
        ).wrapToSurvey()
        val survey = ValidationUseCaseImpl(buildScriptEngine(), questionComponent).validate(false).survey

        assertTrue(survey.groups[0].questions[0].instructionList[0].errors[0] is InstructionError.ScriptError)
    }

    @Test
    fun fwd_reference_errors_are_reflected() {
        val questionComponent1 = Question(
            code = "Q1",
            instructionList = listOf(SimpleState("Q2.value", ReservedCode.Value, isActive = true))
        )
        val questionComponent2 = Question(
            code = "Q2",
            instructionList = listOf(SimpleState("Q1.value", ReservedCode.Value, isActive = true))
        )
        val component = Group("G1", questions = listOf(questionComponent1, questionComponent2)).wrapToSurvey()
        val survey = ValidationUseCaseImpl(buildScriptEngine(), component).validate(false).survey
        assertEquals(
            InstructionError.ForwardDependency(
                Dependency("Q2", ReservedCode.Value)
            ), survey.groups[0].questions[0].instructionList[0].errors[0]
        )
    }
}

fun SurveyComponent.wrapToSurvey(): Survey {
    return when (this) {
        is Survey -> this
        is Group -> Survey(groups = listOf(this))
        is Question -> Group("G1", questions = listOf(this)).wrapToSurvey()
        is Answer -> throw IllegalStateException("We are going too far here!!")
    }
}
