package com.qlarr.surveyengine.model.exposed

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport


@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
enum class ReturnType {
    BOOLEAN, STRING, INT, DOUBLE, LIST, MAP, DATE, FILE;


    fun defaultTextValue(): String {
        return when (this) {
            LIST -> "[]"
            STRING -> ""
            BOOLEAN -> "false"
            DATE -> "1970-01-01 00:00:00"
            INT, DOUBLE -> "0"
            MAP -> "{}"
            FILE -> "{\"filename\":\"\",\"stored_filename\":\"\",\"size\":0,\"type\":\"\"}"
        }
    }

    companion object{
        fun fromString(text:String) = entries.first { it.name.lowercase() == text }
    }
}

@Serializable
data class TypedValue(val returnType: ReturnType, val value: JsonElement)

