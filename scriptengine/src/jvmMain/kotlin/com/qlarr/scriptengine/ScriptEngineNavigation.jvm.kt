package com.qlarr.scriptengine

import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine
import com.qlarr.surveyengine.model.ReturnType
import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.usecase.*
import kotlinx.serialization.json.*
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
        override fun validate(input: List<ScriptValidationInput>): List<ScriptValidationOutput> {
            val scriptParams: Bindings = engine.createBindings()
            // Build JSON array using kotlinx.serialization
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
            scriptParams["instructionList"] = items.toString()
            val result = compiledScript.eval(scriptParams).toString()
            val processed: List<List<ValidationScriptError>> = try {
                jsonMapper.decodeFromString(result)
            } catch (e: Exception) {
                listOf()
            }
            return input.mapIndexed { index, scriptValidationInput ->
                ScriptValidationOutput(scriptValidationInput.componentInstruction, processed[index])
            }
        }
    }
}

actual fun getNavigate(script: String): ScriptEngineNavigate {
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
        script +
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