package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.model.SurveyLang
import com.qlarr.surveyengine.model.exposed.NavigationMode
import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.scriptengine.ScriptEngineValidate
import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
interface ValidationUseCaseWrapper {
    // Serialized ValidationJsonOutput
    fun validate(): String

    companion object {
        fun create(surveyJson: String): ValidationUseCaseWrapper =
            ValidationUseCaseWrapperImpl(surveyJson)

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
                })
            })
            add(buildJsonObject {
                put("content", buildJsonObject {
                    put("en", buildJsonObject {
                        put("label", "Thank you for taking the time to complete this survey.")
                    })
                })
                put("code", "G2")
                put("groupType", "END")
                put("questions", buildJsonArray {
                })
            })
        }

        fun new(surveyName: String): String {
            val survey = buildJsonObject {
                put("groups", groups(surveyName))
                put("defaultLang", jsonMapper.encodeToJsonElement(SurveyLang.EN))
                put("code", "Survey")
                put("navigationMode", NavigationMode.GROUP_BY_GROUP.name.lowercase())
                put("allowPrevious", true)
                put("skipInvalid", true)
                put("allowIncomplete", true)
                put("allowJump", true)
            }
            return survey.toString()
        }

    }
}


