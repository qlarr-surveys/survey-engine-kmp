package com.qlarr.surveyengine.context.assemble

import com.qlarr.surveyengine.model.Answer
import com.qlarr.surveyengine.model.Group
import com.qlarr.surveyengine.model.Question
import com.qlarr.surveyengine.model.RepeatInfo
import com.qlarr.surveyengine.model.Survey
import com.qlarr.surveyengine.model.SurveyComponent

internal fun List<SurveyComponent>.expandRepeatables(): List<SurveyComponent> =
    flatMap { component ->
        val repeatInfo = component.repeatInfo
        when {
            repeatInfo is RepeatInfo.Repeated -> emptyList()
            component.noErrors() && repeatInfo is RepeatInfo.Repeatable ->
                listOf(component) + repeatInfo.range.map { token -> component.suffixCodes(token, isRoot = true) }
            else -> listOf(component.duplicate(children = component.children.expandRepeatables()))
        }
    }

private fun SurveyComponent.suffixCodes(token: String, isRoot: Boolean): SurveyComponent {
    val newCode = if (isRoot || hasUniqueCode()) "${code}_$token" else code
    val newRepeatInfo = if (isRoot) RepeatInfo.Repeated(token) else repeatInfo
    return when (this) {
        is Group -> copy(
            code = newCode,
            repeatInfo = newRepeatInfo,
            questions = questions.map { it.suffixCodes(token, isRoot = false) as Question }
        )

        is Question -> copy(
            code = newCode,
            repeatInfo = newRepeatInfo,
            answers = answers.map { it.suffixCodes(token, isRoot = false) as Answer }
        )

        is Answer -> copy(
            code = newCode,
            repeatInfo = newRepeatInfo,
            answers = answers.map { it.suffixCodes(token, isRoot = false) as Answer }
        )

        is Survey -> this
    }
}
