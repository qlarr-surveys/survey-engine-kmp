package com.qlarr.surveyengine.context.assemble

import com.qlarr.surveyengine.ext.remapComponentCodes
import com.qlarr.surveyengine.model.Answer
import com.qlarr.surveyengine.model.Group
import com.qlarr.surveyengine.model.Instruction
import com.qlarr.surveyengine.model.Question
import com.qlarr.surveyengine.model.REPEAT_TOKEN_PLACEHOLDER
import com.qlarr.surveyengine.model.RepeatInfo
import com.qlarr.surveyengine.model.ReservedCode
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
                    component
                        .suffixCodes(token, isRoot = true) { c -> if (c in codes) "${c}_$token" else c }
                        .withRepeatRelevance(repeatInfo.relevanceInstruction.replace(REPEAT_TOKEN_PLACEHOLDER, token))
                }
            }

            else -> listOf(component.duplicate(children = component.children.expandRepeatables()))
        }
    }

private fun SurveyComponent.suffixableCodes(isRoot: Boolean): Set<String> =
    (if (isRoot || hasUniqueCode()) setOf(code) else emptySet()) +
        children.flatMap { it.suffixableCodes(isRoot = false) }

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

private fun SurveyComponent.withRepeatRelevance(tokenRelevance: String): SurveyComponent {
    val existing = instructionList
        .filterIsInstance<Instruction.SimpleState>()
        .firstOrNull { it.reservedCode == ReservedCode.ConditionalRelevance && it.noErrors() && it.text != "true" }
    val text = if (existing != null) "(${existing.text}) && ($tokenRelevance)" else tokenRelevance
    val instruction = existing?.withNewText(text) ?: Instruction.SimpleState(text, ReservedCode.ConditionalRelevance)
    return replaceOrAddInstruction(instruction)
}
