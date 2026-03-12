package com.qlarr.surveyengine.model

import kotlinx.serialization.Serializable

@Serializable
data class DeserializationIssue(
    val path: String,
    val message: String,
    val instructionJsonFragment: String = ""
) {
    fun simplified() = message
}


data class DeserializationResult(
    val survey: Survey,
    val issues: List<DeserializationIssue> = emptyList()
)
