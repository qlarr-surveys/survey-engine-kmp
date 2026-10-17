package com.qlarr.surveyengine.usecase

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class Calculation(
    val code: String,
    val expression: String,
    val errors: List<CalculationError> = listOf()
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("name")
internal sealed class CalculationError {
    @Serializable
    @SerialName("ScriptError")
    data class ScriptError(val message: String, val start: Int, val end: Int) : CalculationError()

    @Serializable
    @SerialName("InvalidCode")
    data object InvalidCode : CalculationError()

    @Serializable
    @SerialName("DuplicateCode")
    data object DuplicateCode : CalculationError()
}

@Serializable
internal data class CalculationRuntimeError(val name: String, val message: String)

@Serializable
internal data class CalculationOutput(
    val results: Map<String, JsonElement> = mapOf(),
    val errors: Map<String, CalculationRuntimeError> = mapOf()
)
