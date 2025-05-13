package com.qlarr.scriptengine

import com.qlarr.surveyengine.model.exposed.ReturnType
import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.usecase.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer


actual fun getValidate(): ScriptEngineValidate {

    return object : ScriptEngineValidate {
        override fun validate(input: String): String {

            val scriptInput = jsonMapper.decodeFromString(ListSerializer(serializer<ScriptValidationInput>()), input)
            // Prepare JSON payload (similar to how it's done in JVM)
            val items = buildJsonArray {
                scriptInput.forEach { validationInput ->
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
            val scriptOutput = scriptInput.mapIndexed { index, scriptValidationInput ->
                ScriptValidationOutput(scriptValidationInput.componentInstruction, processed[index])
            }
            return jsonMapper.encodeToString(ListSerializer(serializer<ScriptValidationOutput>()), scriptOutput)
        }
    }
}


actual fun getNavigate(script: String): ScriptEngineNavigate {
    TODO("Not yet implemented")
}