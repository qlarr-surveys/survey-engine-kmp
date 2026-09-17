package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.model.exposed.*
import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.scriptengine.ScriptEngineNavigate
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

// Malformed input here means the caller built it wrong, and silently falling back to "no quota is
// full" would keep the survey recruiting past its targets. Fail loudly instead.
object MalformedFullQuotasException : Exception()

private fun String.toQuotaCodes(): Set<String> = try {
    jsonMapper.parseToJsonElement(this).jsonArray.map { it.jsonPrimitive.content }.toSet()
} catch (_: Exception) {
    throw MalformedFullQuotasException
}

@OptIn(ExperimentalJsExport::class)
@JsExport
interface NavigationUseCaseWrapper {
    // Serialized NavigationJsonOutput
    @Throws(Throwable::class)
    fun navigate(scriptEngine: ScriptEngineNavigate): String
    fun getNavigationScript(): String
    fun processNavigationResult(scriptResult: String): String

    companion object {
        @Throws(Throwable::class)
        fun init(
            values: String = "{}",
            processedSurvey: String,
            lang: String? = null,
            navigationMode: NavigationMode,
            navigationIndex: NavigationIndex? = null,
            navigationDirection: NavigationDirection = NavigationDirection.Start,
            skipInvalid: Boolean,
            surveyMode: SurveyMode,
            // Serialized JSON array of quota codes that are already full, e.g. ["QT1"]
            fullQuotas: String = "[]"
        ): NavigationUseCaseWrapper {
            return NavigationUseCaseWrapperImpl(
                processedSurvey = processedSurvey,
                skipInvalid = skipInvalid,
                surveyMode = surveyMode,
                values = jsonMapper.parseToJsonElement(values).jsonObject,
                lang = lang,
                navigationMode = navigationMode,
                navigationIndex = navigationIndex,
                navigationDirection = navigationDirection,
                fullQuotas = fullQuotas.toQuotaCodes()
            )
        }
    }
}

