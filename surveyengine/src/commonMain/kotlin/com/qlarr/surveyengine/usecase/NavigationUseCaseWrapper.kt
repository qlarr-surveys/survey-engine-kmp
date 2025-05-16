package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.model.exposed.*
import com.qlarr.surveyengine.model.jsonMapper
import kotlinx.serialization.json.jsonObject
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
            values: String = "{}",
            processedSurvey: String,
            lang: String? = null,
            navigationMode: NavigationMode? = null,
            navigationIndex: NavigationIndex? = null,
            navigationDirection: NavigationDirection = NavigationDirection.Start,
            skipInvalid: Boolean,
            surveyMode: SurveyMode
        ): NavigationUseCaseWrapper {
            return NavigationUseCaseWrapperImpl(
                scriptEngine = scriptEngine,
                processedSurvey = processedSurvey,
                skipInvalid = skipInvalid,
                surveyMode = surveyMode,
                values = jsonMapper.parseToJsonElement(values).jsonObject,
                lang = lang,
                navigationMode = navigationMode,
                navigationIndex = navigationIndex,
                navigationDirection = navigationDirection
            )
        }
    }
}

