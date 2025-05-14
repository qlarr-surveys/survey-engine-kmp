package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.context.assemble.NotSkippedInstructionManifesto
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.exposed.NavigationMode
import com.qlarr.surveyengine.model.exposed.ResponseField
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
internal data class ValidationJsonOutput(
    val survey: JsonObject = buildJsonObject {},
    val schema: List<ResponseField> = listOf(),
    val impactMap: StringImpactMap = mapOf(),
    val componentIndexList: List<ComponentIndex> = listOf(),
    val skipMap: Map<String, List<NotSkippedInstructionManifesto>> = mapOf(),
    val script: String = ""
) {
    fun toValidationOutput() = ValidationOutput(
        impactMap = impactMap,
        schema = schema,
        survey = jsonMapper.decodeFromString<Survey>(survey.toString()),
        script = script,
        componentIndexList = componentIndexList,
        skipMap = skipMap
    )

    fun surveyNavigationData(): SurveyNavigationData {
        return SurveyNavigationData(
            allowJump = survey["allowJump"]?.jsonPrimitive?.booleanOrNull ?: true,
            allowPrevious = survey["allowPrevious"]?.jsonPrimitive?.booleanOrNull ?: true,
            skipInvalid = survey["skipInvalid"]?.jsonPrimitive?.booleanOrNull ?: true,
            allowIncomplete = survey["allowIncomplete"]?.jsonPrimitive?.booleanOrNull ?: true,
            navigationMode = NavigationMode.fromString(survey["navigationMode"]?.jsonPrimitive?.contentOrNull)
        )
    }
}

fun JsonObject.defaultLang(): String =
    (this["defaultLang"] as? JsonObject)?.get("code")?.jsonPrimitive?.content ?: SurveyLang.EN.code

fun JsonObject.defaultSurveyLang(): SurveyLang =
    try {
        jsonMapper.decodeFromJsonElement<SurveyLang>(this["defaultLang"] as JsonObject)
    } catch (e: Exception) {
        SurveyLang.EN
    }


fun JsonObject.additionalLang(): List<SurveyLang> =
    try {
        jsonMapper.decodeFromString<List<SurveyLang>>(this["additionalLang"].toString())
    } catch (e: Exception) {
        listOf()
    }

fun JsonObject.availableLangByCode(code: String?): SurveyLang {
    val defaultLang = defaultSurveyLang()
    return if (code == null || defaultLang.code == code) {
        defaultLang
    } else {
        additionalLang().firstOrNull { it.code == code } ?: defaultLang
    }
}

data class SurveyNavigationData(
    val navigationMode: NavigationMode = NavigationMode.GROUP_BY_GROUP,
    val allowPrevious: Boolean = true,
    val skipInvalid: Boolean = true,
    val allowIncomplete: Boolean = true,
    val allowJump: Boolean = true
)