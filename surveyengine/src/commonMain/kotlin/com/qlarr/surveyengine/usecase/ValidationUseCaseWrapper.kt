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
        fun create(scriptEngine: ScriptEngineValidate, surveyJson: String): ValidationUseCaseWrapper =
            ValidationUseCaseWrapperImpl(scriptEngine, surveyJson)

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


