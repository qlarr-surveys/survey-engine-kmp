package com.qlarr.surveyengine.context.assemble

import com.qlarr.surveyengine.ext.remapComponentCodes
import com.qlarr.surveyengine.model.Answer
import com.qlarr.surveyengine.model.Group
import com.qlarr.surveyengine.model.Instruction
import com.qlarr.surveyengine.model.Question
import com.qlarr.surveyengine.model.RepeatInfo
import com.qlarr.surveyengine.model.Survey
import com.qlarr.surveyengine.model.SurveyComponent

internal fun List<SurveyComponent>.expandRepeatables(): List<SurveyComponent> =
    flatMap { component ->
        val repeatInfo = component.repeatInfo
        when {
            repeatInfo is RepeatInfo.Repeated -> emptyList()
            component.noErrors() && repeatInfo is RepeatInfo.Repeatable -> {
                val codes = component.suffixableCodes(isRoot = true)
                listOf(component) + repeatInfo.range.map { token ->
                    component.expand(token) { c -> if (c in codes) "${c}_$token" else c }
                }
            }

            else -> listOf(component.duplicate(children = component.children.expandRepeatables()))
        }
    }

private fun SurveyComponent.suffixableCodes(isRoot: Boolean): Set<String> =
    (if (isRoot || hasUniqueCode()) setOf(code) else emptySet()) +
        children.flatMap { it.suffixableCodes(isRoot = false) }

private fun SurveyComponent.expand(token: String, remap: (String) -> String): SurveyComponent =
    suffixCodes(token, isRoot = true, remap)

private fun SurveyComponent.suffixCodes(
    token: String,
    isRoot: Boolean,
    remap: (String) -> String
): SurveyComponent {
    val newCode = if (isRoot || hasUniqueCode()) "${code}_$token" else code
    val newRepeatInfo = if (isRoot) RepeatInfo.Repeated(token) else repeatInfo
    val newInstructions = instructionList.map { instruction ->
        if (instruction is Instruction.State) instruction.withNewText(instruction.text.remapComponentCodes(remap))
        else instruction
    }
    return when (this) {
        is Group -> copy(
            code = newCode,
            instructionList = newInstructions,
            repeatInfo = newRepeatInfo,
            questions = questions.map { it.suffixCodes(token, isRoot = false, remap) as Question }
        )

        is Question -> copy(
            code = newCode,
            instructionList = newInstructions,
            repeatInfo = newRepeatInfo,
            answers = answers.map { it.suffixCodes(token, isRoot = false, remap) as Answer }
        )

        is Answer -> copy(
            code = newCode,
            instructionList = newInstructions,
            repeatInfo = newRepeatInfo,
            answers = answers.map { it.suffixCodes(token, isRoot = false, remap) as Answer }
        )

        is Survey -> this
    }
}
