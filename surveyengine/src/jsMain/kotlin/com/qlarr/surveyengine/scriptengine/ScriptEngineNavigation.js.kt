package com.qlarr.surveyengine.scriptengine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

// getNavigate()/getValidate() are top-level `expect` functions consumed in-module by the JVM/iOS
// targets. They are not @JsExport-able as-is, so JS consumers (outside the module) get no way to build
// the ScriptEngineNavigate/ScriptEngineValidate that the use-case wrappers require. These exported
// factories bridge that gap.
@OptIn(ExperimentalJsExport::class)
@JsExport
fun createNavigationEngine(): ScriptEngineNavigate = getNavigate()

@OptIn(ExperimentalJsExport::class)
@JsExport
fun createValidationEngine(): ScriptEngineValidate = getValidate()

// commonScript()/engineScript() return CommonScriptProvider/EngineScriptProvider, which are not
// @JsExport-able. These wrappers expose the raw script strings so JS consumers of the npm package can
// read them directly.
@OptIn(ExperimentalJsExport::class)
@JsExport
fun getCommonScript(): String = commonScript().script

@OptIn(ExperimentalJsExport::class)
@JsExport
fun getEngineScript(): String = engineScript().script


actual fun getValidate(): ScriptEngineValidate {

    return object : ScriptEngineValidate {
        override fun validate(input: String): String {
            val items = Json.parseToJsonElement(input).jsonArray
            val result = validateCode(items.toString())
            return result
        }
    }
}

// Compiles the engine script (common + initial, which defines the global `navigate` function and its
// helpers) once into a reusable function, then calls `navigate(JSON.parse(params))`. This mirrors the
// JVM implementation, which compiles the same script into a GraalVM context. The script text is passed
// as an argument to the JS factory so the Kotlin compiler's identifier mangling never touches it.
private fun compileNavigate(engineScript: String): (String) -> String {
    val factory = js(
        "(function(src){ var fn = new Function('params', src + '\\n;return navigate(JSON.parse(params));'); return fn; })"
    )
    return factory(engineScript).unsafeCast<(String) -> String>()
}

actual fun getNavigate(): ScriptEngineNavigate {
    val navigateFn = compileNavigate(engineScript().script)
    return object : ScriptEngineNavigate {
        override fun navigate(script: String): String {
            return navigateFn(script)
        }
    }
}