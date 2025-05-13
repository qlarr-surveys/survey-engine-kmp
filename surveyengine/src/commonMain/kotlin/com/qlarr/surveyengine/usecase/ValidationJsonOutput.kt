package com.qlarr.surveyengine.usecase

import kotlinx.serialization.json.*
import com.qlarr.surveyengine.context.assemble.NotSkippedInstructionManifesto
import com.qlarr.surveyengine.ext.flatten
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.exposed.NavigationMode
import com.qlarr.surveyengine.model.exposed.ResponseField
import kotlinx.serialization.Serializable

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

    fun toDesignerInput(): DesignerInput = DesignerInput(
        survey.flatten(),
        componentIndexList
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

    companion object {
        private fun groups(surveyName: String) = buildJsonArray {
            add(buildJsonObject {
                put("code", "G1")
                put("content", buildJsonObject {
                    put("en", buildJsonObject {
                        put("label", surveyName)
                    })
                })
                put("groupType", "GROUP")
                put("questions", buildJsonArray {
                    add(buildJsonObject {
                        put("content", buildJsonObject {
                            put("en", buildJsonObject {
                                put("label", "Sample Text Question")
                            })
                        })
                        put("code", "Q1")
                        put("type", "text_display")
                    })
                })
            })
            add(buildJsonObject {
                put("content", buildJsonObject {
                    put("en", buildJsonObject {
                        put("label", "End Page")
                    })
                })
                put("code", "G2")
                put("groupType", "END")
                put("questions", buildJsonArray {
                    add(buildJsonObject {
                        put("content", buildJsonObject {
                            put("en", buildJsonObject {
                                put("label", "Bye Question")
                            })
                        })
                        put("code", "Q2")
                        put("type", "text_display")
                    })
                })
            })
        }

        fun new(surveyName: String) = ValidationJsonOutput(
            survey = buildJsonObject {
                put("groups", groups(surveyName))
                put("defaultLang", jsonMapper.encodeToJsonElement(SurveyLang.EN))
                put("code", "Survey")
                put("navigationMode", NavigationMode.GROUP_BY_GROUP.name.lowercase())
                put("allowPrevious", true)
                put("skipInvalid", true)
                put("allowIncomplete", true)
                put("allowJump", true)
            }
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