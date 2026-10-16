package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.scriptengine.ScriptEngineCalculate
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
interface CalculationUseCaseWrapper {
    fun validate(calculations: String): String
    fun calculate(scriptEngine: ScriptEngineCalculate, calculations: String, responses: String): String

    companion object {
        fun create(processedSurvey: String): CalculationUseCaseWrapper =
            CalculationUseCaseWrapperImpl(processedSurvey)
    }
}
