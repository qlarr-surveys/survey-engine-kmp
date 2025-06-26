package com.qlarr.surveyengine.scriptengine

import com.qlarr.surveyengine.model.ComponentInstruction
import com.qlarr.surveyengine.model.InstructionError
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

expect fun getNavigate(): ScriptEngineNavigate
expect fun getValidate(): ScriptEngineValidate


@OptIn(ExperimentalJsExport::class)
@JsExport
interface ScriptEngineValidate {
    // The script is: List<ScriptValidationInput>
    // The output is: List<ScriptValidationOutput>
    fun validate(input: String): String

}

@OptIn(ExperimentalJsExport::class)
@JsExport
interface ScriptEngineNavigate {
    // The script is: NavigationInstructionsInput
    // The output is: NavigationInstructionsInput
    fun navigate(script: String): String
}

@Serializable
data class ScriptValidationInput(
    val componentInstruction: ComponentInstruction,
    val dependencies: List<String>
)

@Serializable
data class ScriptValidationOutput(
    val componentInstruction: ComponentInstruction,
    val result: List<ValidationScriptError>
)

@Serializable
data class ValidationScriptError(
    val message: String,
    val start: Int,
    val end: Int
){
    fun toBindingError() = InstructionError.ScriptError(
        message, start, end
    )
}
