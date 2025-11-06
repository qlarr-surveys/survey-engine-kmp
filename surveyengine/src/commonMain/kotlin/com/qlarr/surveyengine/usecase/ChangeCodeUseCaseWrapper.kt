package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.scriptengine.ScriptEngineValidate
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
interface ChangeCodeUseCaseWrapper {
    // Serialized ValidationJsonOutput
    fun changeCode(from: String, to: String): String

    companion object {
        fun create(
            processedSurvey: String
        ): ChangeCodeUseCaseWrapper =
            ChangeCodeUseCaseWrapperImpl(processedSurvey)
    }
}


