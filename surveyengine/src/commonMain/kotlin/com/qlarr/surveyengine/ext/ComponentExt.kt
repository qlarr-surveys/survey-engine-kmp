package com.qlarr.surveyengine.ext

import com.qlarr.surveyengine.context.assemble.parents
import com.qlarr.surveyengine.model.ChildlessComponent
import com.qlarr.surveyengine.model.Dependent
import com.qlarr.surveyengine.model.Instruction
import com.qlarr.surveyengine.model.SurveyElementType


fun String.splitToComponentCodes(): List<String> {
    val regex = Regex(VALID_COMPONENT_CODE_PATTERN)
    return regex.findAll(this).map { it.value }.toList()
}

fun String.isGroupCode(): Boolean {
    return this.matches(Regex(VALID_GROUP_CODE))
}

fun String.isAnswerCode(): Boolean {
    return this.matches(Regex(VALID_SINGLE_ANSWER_CODE))
}

fun String.isQuestionCode(): Boolean {
    return this.matches(Regex(VALID_QUESTION_CODE))
}

fun String.isSurveyCode(): Boolean {
    return this == "Survey"
}

fun String.isUniqueCode() = this.isQuestionCode()
        || this.isGroupCode()
        || this.isSurveyCode()

fun String.childrenName() = when {
    this.isQuestionCode() -> "answers"
    this.isGroupCode() -> "questions"
    this.isSurveyCode() -> "groups"
    this.isAnswerCode() -> "answers"
    else -> throw IllegalStateException("illegal code ")
}

private val GTHIS_REGEX = Regex("\\bGthis\\b")
private val QTHIS_REGEX = Regex("\\bQthis\\b")

fun List<ChildlessComponent>.withReplacements(replacements: MutableMap<String, String>): List<ChildlessComponent> =
    map { component ->
        component.copy(
            instructionList = component.instructionList.map { instruction ->
                if (instruction is Instruction.IsRunnable && instruction.runnableInstruction().isActive) {
                    var replacement = false
                    val groupCode by lazy {
                        if (component.surveyElementType == SurveyElementType.GROUP){
                            component.code
                        } else {
                            parents(component.code).last { parent -> parent.startsWith("G") }
                        }
                    }
                    val questionCode by lazy {
                        if (component.surveyElementType == SurveyElementType.QUESTION){
                            component.code
                        } else {
                            parents(component.code).last { parent -> parent.startsWith("Q") }
                        }
                    }
                    val newInstruction = instruction.replaceThis(
                        elementType = component.surveyElementType,
                        questionCode = { replacement = true; questionCode },
                        groupCode = { replacement = true; groupCode },
                    )
                    if (replacement) {
                        replacements["${component.code}.${instruction.code}"] =
                            (newInstruction as Instruction.IsRunnable).runnableInstruction().text
                    }
                    newInstruction
                } else {
                    instruction
                }
            }
        )
    }

fun Instruction.IsRunnable.replaceThis(
    elementType: SurveyElementType,
    questionCode: () -> String,
    groupCode: () -> String,
) = when (elementType) {
    SurveyElementType.GROUP -> {
        val text = runnableInstruction().text
        val replaced = if (GTHIS_REGEX.containsMatchIn(text))
            text.replace(GTHIS_REGEX, groupCode())
        else text
        this.withNewText(replaced)
    }

    SurveyElementType.QUESTION, SurveyElementType.ANSWER -> {
        val text = runnableInstruction().text
        val withGroup = if (GTHIS_REGEX.containsMatchIn(text))
            text.replace(GTHIS_REGEX, groupCode())
        else text
        val withQuestion = if (QTHIS_REGEX.containsMatchIn(withGroup))
            withGroup.replace(QTHIS_REGEX, questionCode())
        else withGroup
        this.withNewText(withQuestion)
    }

    else -> this as Instruction
}


