package com.qlarr.surveyengine.scriptengine

import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.ResourceLimits
import javax.script.Bindings
import javax.script.Compilable
import javax.script.CompiledScript
import javax.script.ScriptEngine

actual fun getValidate(): ScriptEngineValidate {

    val classLoader = object {}.javaClass.classLoader
    val script =
        classLoader.getResourceAsStream("survey-engine-script/survey-engine-script.min.js")!!.reader().readText()
    val engine: ScriptEngine = GraalJSScriptEngine.create(null,
        Context.newBuilder("js")
            .allowHostAccess(HostAccess.NONE)
            .allowHostClassLookup { false }
            .resourceLimits(
                ResourceLimits.newBuilder()
                    .statementLimit(1000000, null)
                    .build()
            ) // Set resource limits
            .allowIO(false)
            .option("js.ecmascript-version", "2021"))
    val compiledScript: CompiledScript = (engine as Compilable).compile(
        "$script;" +
                "const EMScript = typeof globalThis !== 'undefined' ? globalThis.EMScript : this.EMScript;" +
                "EMScript.validateCode(instructionList);"
    )


    return object : ScriptEngineValidate {
        override fun validate(input: String): String {
            val scriptParams: Bindings = engine.createBindings()
            // Build JSON array using kotlinx.serialization
            val items = Json.parseToJsonElement(input).jsonArray
            scriptParams["instructionList"] = items.toString()
            val result = compiledScript.eval(scriptParams).toString()
            return result
        }
    }
}

actual fun getNavigate(): ScriptEngineNavigate {
    val engine: ScriptEngine = GraalJSScriptEngine.create(null,
        Context.newBuilder("js")
            .allowHostAccess(HostAccess.NONE)
            .allowHostClassLookup { false }
            .resourceLimits(
                ResourceLimits.newBuilder()
                    .statementLimit(1000000, null)
                    .build()
            ) // Set resource limits
            .allowIO(false)
            .option("js.ecmascript-version", "2021"))

    val compiledScript: CompiledScript = (engine as Compilable).compile(
        engineScript().script +
                "\nnavigate(JSON.parse(params)) "
    )
    return object : ScriptEngineNavigate {
        override fun navigate(script: String): String {
            val scriptParams: Bindings = engine.createBindings()
            scriptParams["params"] = script
            return compiledScript.eval(scriptParams).toString()
        }

    }


}