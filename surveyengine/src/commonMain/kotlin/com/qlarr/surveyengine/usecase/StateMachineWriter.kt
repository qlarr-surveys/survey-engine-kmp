package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.ext.mapToJsonObject
import com.qlarr.surveyengine.model.ChildlessComponent
import com.qlarr.surveyengine.model.Dependent
import com.qlarr.surveyengine.model.Instruction
import com.qlarr.surveyengine.model.Instruction.State
import com.qlarr.surveyengine.model.ReservedCode
import kotlinx.serialization.json.*
import kotlinx.serialization.*

internal class StateMachineWriter(scriptInput: ScriptInput) {
    private val bindings = scriptInput.bindings
    private val formatBindings = scriptInput.formatBindings
    private val jsComponents = scriptInput.contextComponents
    private val impactMap = scriptInput.dependencyMapBundle.first
    private val state = mutableMapOf<String, JsonElement>()
    private val qlarrVariables = mutableMapOf<String, JsonElement>()
    private val qlarrDependents = mutableMapOf<String, JsonElement>()

    fun state():JsonObject {

        jsComponents.forEach {
            it.writeSystemInstruction()
        }
        state["qlarrVariables"] = JsonObject(qlarrVariables)
        state["qlarrDependents"] = JsonObject(qlarrDependents)
        return JsonObject(state)
    }


    private fun ChildlessComponent.writeSystemInstruction() {
        val componentVariables = mutableMapOf<String, JsonElement>()
        val componentDependents = mutableMapOf<String, JsonElement>()

        instructionList.filterIsInstance<Instruction.Reference>().forEach { instruction ->
            componentVariables[instruction.code] = formatBindings[Dependent(code, instruction.code)]!!
        }
        bindings.filter { it.key.componentCode == code }.forEach {
            componentVariables[it.key.reservedCode.code] = it.value
        }
        instructionList.filterIsInstance<State>().forEach { reservedInstruction ->
            if (isDependency(code, reservedInstruction.reservedCode)) {
                val dependentsList = buildJsonArray {
                    writeDependentFunctions(code, reservedInstruction.reservedCode).forEach {
                        add(it)
                    }
                }
                if (dependentsList.isNotEmpty()) {
                    componentDependents[reservedInstruction.code] = dependentsList
                }
            }
        }
        if (componentDependents.keys.isNotEmpty()) {
            qlarrDependents[code] = JsonObject(componentDependents)
        }
        if (componentVariables.keys.isNotEmpty() || shouldHaveEmptyObject()) {
            qlarrVariables[code] = JsonObject(componentVariables)
        }
    }

    private fun writeDependentFunctions(componentCode: String, reservedCode: ReservedCode): List<JsonArray> {
        return getDependents(componentCode, reservedCode).map {
            buildJsonArray {
                add(JsonPrimitive(it.componentCode))
                add(JsonPrimitive(it.instructionCode))

            }
        }
    }

    // Method removed as we're now using native Kotlin Lists instead of JSONArray

    private fun isDependency(qualifiedCode: String, reservedCode: ReservedCode): Boolean {
        return impactMap.keys.any { it.componentCode == qualifiedCode && it.reservedCode == reservedCode }
    }

    private fun getDependents(qualifiedCode: String, reservedCode: ReservedCode): List<Dependent> {
        val key = impactMap.keys.firstOrNull { it.componentCode == qualifiedCode && it.reservedCode == reservedCode }
        return impactMap[key] ?: listOf()
    }

    private fun ChildlessComponent.shouldHaveEmptyObject(): Boolean =
        instructionList.any { it.code == ReservedCode.Value.code }
}