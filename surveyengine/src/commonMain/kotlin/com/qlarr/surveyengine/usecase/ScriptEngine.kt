package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.model.InstructionError
import com.qlarr.surveyengine.model.ComponentInstruction
import kotlinx.serialization.Serializable


interface ScriptEngineValidate {
    // The script is: List<ScriptValidationInput>
    // The output is: List<ScriptValidationOutput>
    fun validate(input: List<ScriptValidationInput>): List<ScriptValidationOutput>
}


interface ScriptEngineNavigate {
    // The script is: NavigationInstructionsInput
    // The output is: NavigationInstructionsInput
    fun navigate(script: String): String
}

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
