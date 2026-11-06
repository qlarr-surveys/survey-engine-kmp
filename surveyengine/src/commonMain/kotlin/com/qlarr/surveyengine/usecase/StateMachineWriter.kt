package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.model.ChildlessComponent
import com.qlarr.surveyengine.model.Dependency
import com.qlarr.surveyengine.model.Dependent
import com.qlarr.surveyengine.model.Instruction
import com.qlarr.surveyengine.model.Instruction.State
import com.qlarr.surveyengine.model.ReservedCode
import kotlinx.serialization.json.*

internal class StateMachineWriter(scriptInput: ScriptInput) {
    private val bindingsByComponent = scriptInput.bindings.entries.groupBy { it.key.componentCode }
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

        instructionList.filterIsInstance<Instruction.Format>().forEach { instruction ->
            componentVariables[instruction.code] = formatBindings[Dependent(code, instruction.code)] ?: JsonPrimitive("")
        }
        bindingsByComponent[code]?.forEach {
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
        return impactMap.containsKey(Dependency(qualifiedCode, reservedCode))
    }

    private fun getDependents(qualifiedCode: String, reservedCode: ReservedCode): List<Dependent> {
        return impactMap[Dependency(qualifiedCode, reservedCode)] ?: listOf()
    }

    private fun ChildlessComponent.shouldHaveEmptyObject(): Boolean =
        instructionList.any { it.code == ReservedCode.Value.code }
}