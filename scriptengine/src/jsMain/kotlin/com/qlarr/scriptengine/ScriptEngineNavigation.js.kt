package com.qlarr.scriptengine

import com.qlarr.surveyengine.model.ReturnType
import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.usecase.*
import kotlinx.serialization.json.*
import kotlin.reflect.typeOf


actual fun getValidate(): ScriptEngineValidate {

    return object : ScriptEngineValidate {
        override fun validate(input: List<ScriptValidationInput>): List<ScriptValidationOutput> {
            // Prepare JSON payload (similar to how it's done in JVM)
            val items = buildJsonArray {
                input.forEach { validationInput ->
                    addJsonObject {
                        validationInput.componentInstruction.instruction.run {
                            put("script", if (returnType == ReturnType.STRING && !isActive) "\"$text\"" else text)
                        }
                        putJsonArray("allowedVariables") {
                            validationInput.dependencies.forEach { add(JsonPrimitive(it)) }
                        }
                    }
                }
            }

            console.log(items)
            val result = validateCode(items.toString())
            console.log(result)

            // Decode the result into the expected Kotlin types
            val processed: List<List<ValidationScriptError>> = try {
                jsonMapper.decodeFromString(result)
            } catch (e: Exception) {
                listOf() // Handle parsing issues gracefully
            }

            // Map results back to the expected output
            return input.mapIndexed { index, scriptValidationInput ->
                ScriptValidationOutput(scriptValidationInput.componentInstruction, processed[index])
            }
        }
    }
}


actual fun getNavigate(script: String): ScriptEngineNavigate {
    TODO("Not yet implemented")
}