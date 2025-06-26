package com.qlarr.surveyengine.scriptengine

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.JavaScriptCore.JSContext
import platform.JavaScriptCore.objectForKeyedSubscript
import kotlin.Exception
import kotlin.IllegalStateException
import kotlin.OptIn
import kotlin.RuntimeException
import kotlin.String

actual fun getNavigate(): ScriptEngineNavigate {
    val context = JSContext()

    // Set up error handling
    context.exceptionHandler = { _, exception ->
        println("JavaScript Error: ${exception?.toString()}")
    }

    context.evaluateScript(engineScript().script)

    return object : ScriptEngineNavigate {
        override fun navigate(script: String): String {
            return try {
                // Call the navigate function with the JSON string parameter
                val result = context.evaluateScript("navigate($script)")
                result?.toString() ?: ""
            } catch (e: Exception) {
                throw RuntimeException("JavaScript execution error: ${e.message}", e)
            }
        }
    }
}


@OptIn(ExperimentalForeignApi::class)
actual fun getValidate(): ScriptEngineValidate {

    // Load the JavaScript file from the bundle
    val bundle = NSBundle.mainBundle
    val path = bundle.pathForResource("survey-engine-script", "min.js", "survey-engine-script")
        ?: throw IllegalStateException("Could not find common_script.js in bundle")
    val scriptContent = NSString.stringWithContentsOfFile(
        path = path,
        encoding = NSUTF8StringEncoding,
        error = null
    ) ?: throw IllegalStateException("Could not read common_script.js")

    // Create JavaScript context
    val context = JSContext()

    // Set up security restrictions
    context.exceptionHandler = { context, exception ->
        println("JavaScript exception: ${exception?.toString()}")
    }

    // Evaluate the main script
    context.evaluateScript(scriptContent as String)

    // Prepare the validation function
    val validationScript = """
        const EMScript = typeof globalThis !== 'undefined' ? globalThis.EMScript : this.EMScript;
        function validateInstructions(instructionList) {
            try {
                return EMScript.validateCode(instructionList);
            } catch (error) {
                console.error('Validation error:', error);
                return [];
            }
        }
    """.trimIndent()

    context.evaluateScript(validationScript)

    return object : ScriptEngineValidate {
        override fun validate(input: String): String {
            val items = Json.parseToJsonElement(input).jsonArray
            // Execute validation in JavaScript context
            val validateFunction = context.objectForKeyedSubscript("validateInstructions")
            val result = validateFunction?.callWithArguments(listOf(items.toString()))
            val resultString = result?.toString() ?: "[]"
            return resultString
        }
    }
}