package com.qlarr.surveyengine.common

import com.qlarr.surveyengine.model.SurveyComponent

fun SurveyComponent.getErrorsCount(): Int {
    var returnResult = errors.size
    instructionList.forEach { instruction ->
        returnResult += instruction.errors.size
    }
    children.forEach { component ->
        returnResult += component.getErrorsCount()
    }

    return returnResult
}
