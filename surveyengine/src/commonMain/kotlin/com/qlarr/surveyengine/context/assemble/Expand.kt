package com.qlarr.surveyengine.context.assemble

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
        val remapped = instruction.remapComponentCodes(remap)
        if (remapped is Instruction.IsRunnable) {
            val text = remapped.runnableInstruction().text
            if (text.contains(REPEAT_TOKEN_PLACEHOLDER)) {
                remapped.withNewText(text.replace(REPEAT_TOKEN_PLACEHOLDER, token))
            } else remapped
        } else remapped
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
            answers = answers.map { it.suffixCodes(token, isRoot = false, remap) as Answer }
        )

        is Survey -> this
    }
}

private fun SurveyComponent.withRepeatRelevance(tokenRelevance: String): SurveyComponent =
    replaceOrAddInstruction(Instruction.SimpleState(tokenRelevance, ReservedCode.ConditionalRelevance))

internal fun List<SurveyComponent>.copyRepeatedErrorsToRepeatable(): List<SurveyComponent> =
    map { component ->
        val repeatable = component.repeatInfo as? RepeatInfo.Repeatable
        if (component.noErrors() && repeatable != null && repeatable.range.isNotEmpty()) {
            val firstCopy = firstOrNull { it.code == "${component.code}_${repeatable.range.first()}" }
            if (firstCopy != null) component.mergeErrorsFrom(firstCopy, isRoot = true) else component
        } else {
            component.duplicate(children = component.children.copyRepeatedErrorsToRepeatable())
        }
    }

private fun SurveyComponent.mergeErrorsFrom(copy: SurveyComponent, isRoot: Boolean): SurveyComponent {
    val injectedRelevance = if (isRoot) {
        copy.instructionList.firstOrNull {
            it is Instruction.State && it.reservedCode == ReservedCode.ConditionalRelevance
        }
    } else null
    val copyInstructions = copy.instructionList.filter { it !== injectedRelevance }

    val newInstructions = instructionList.mapIndexed { index, instruction ->
        val copyInstruction = copyInstructions.getOrNull(index) ?: return@mapIndexed instruction
        copyInstruction.errors.filterNot { it in instruction.errors }.fold(instruction) { acc, error -> acc.addError(error) }
    }
    val newChildren = children.mapIndexed { index, child ->
        copy.children.getOrNull(index)?.let { child.mergeErrorsFrom(it, isRoot = false) } ?: child
    }

    if (!isRoot) {
        return duplicate(instructionList = newInstructions, children = newChildren)
    }
    val repeatable = (repeatInfo as RepeatInfo.Repeatable)
        .copy(relevanceInstructionErrors = injectedRelevance?.errors ?: listOf())
    return when (this) {
        is Group -> copy(instructionList = newInstructions, repeatInfo = repeatable, questions = newChildren.filterIsInstance<Question>())
        is Question -> copy(instructionList = newInstructions, repeatInfo = repeatable, answers = newChildren.filterIsInstance<Answer>())
        is Answer, is Survey -> this
    }
}
