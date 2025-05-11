package com.qlarr.surveyengine.model

import kotlinx.serialization.Serializable



@Serializable
data class ResponseField(
    val componentCode: String,
    val columnName: ColumnName,
    val dataType: ReturnType
) {
    @Suppress("unused")
    fun toValueKey() = "$componentCode.${columnName.name.lowercase()}"
}


enum class ColumnName {
    VALUE,
    ORDER,
    PRIORITY;
}