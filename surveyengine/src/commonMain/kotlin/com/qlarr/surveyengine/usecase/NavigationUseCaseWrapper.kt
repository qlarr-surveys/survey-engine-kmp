package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.model.exposed.NavigationUseCaseInput
import com.qlarr.surveyengine.model.jsonMapper
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
interface NavigationUseCaseWrapper {
    // Serialized NavigationJsonOutput
    fun navigate(): String
    fun getNavigationScript(): String
    fun processNavigationResult(scriptResult: String): String

    companion object {
        fun init(
            scriptEngine: ScriptEngineNavigate,
            useCaseInput:String
        ): NavigationUseCaseWrapper {
            val input = jsonMapper.decodeFromString(NavigationUseCaseInput.serializer(), useCaseInput)
            return NavigationUseCaseWrapperImpl(
                scriptEngine = scriptEngine,
                processedSurvey = input.processedSurvey,
                skipInvalid = input.skipInvalid,
                surveyMode = input.surveyMode,
                values = input.values,
                lang = input.lang,
                navigationMode = input.navigationMode,
                navigationIndex = input.navigationIndex,
                navigationDirection = input.navigationDirection
            )
        }
    }
}

