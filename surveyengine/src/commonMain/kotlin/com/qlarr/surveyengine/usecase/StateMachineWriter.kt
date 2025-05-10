package com.qlarr.surveyengine.usecase

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import com.qlarr.surveyengine.ext.mapToJsonObject
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.Instruction.State

internal class StateMachineWriter(scriptInput: ScriptInput) {
    private val bindings = scriptInput.bindings
    private val formatBindings = scriptInput.formatBindings
    private val jsComponents = scriptInput.contextComponents
    private val impactMap = scriptInput.dependencyMapBundle.first
    private val state = mutableMapOf<String,Any>()
    private val qlarrVariables = mutableMapOf<String,Any>()
    private val qlarrDependents = mutableMapOf<String,Any>()

    fun state(): JsonObject {

        jsComponents.forEach {
            it.writeSystemInstruction()
        }
        state["qlarrVariables"] = qlarrVariables
        state["qlarrDependents"] = qlarrDependents
        return mapToJsonObject(state)
    }


    private fun ChildlessComponent.writeSystemInstruction() {
        val componentVariables = mutableMapOf<String,Any>()
        val componentDependents = mutableMapOf<String,Any>()

        instructionList.filterIsInstance<Instruction.Reference>().forEach { instruction ->
            componentVariables[instruction.code] = formatBindings[Dependent(code, instruction.code)]!!
        }
        bindings.filter { it.key.componentCode == code }.forEach {
            componentVariables[it.key.reservedCode.code] = it.value
        }
        instructionList.filterIsInstance<State>().forEach { reservedInstruction ->
            if (isDependency(code, reservedInstruction.reservedCode)) {
                val dependentsList = mutableListOf<List<String>>()
                writeDependentFunctions(code, reservedInstruction.reservedCode).forEach {
                    dependentsList.add(it)
                }
                if (dependentsList.isNotEmpty()) {
                    componentDependents[reservedInstruction.code] = dependentsList
                }
            }
        }
        if (componentDependents.keys.isNotEmpty()) {
            qlarrDependents[code] = componentDependents
        }
        if (componentVariables.keys.isNotEmpty() || shouldHaveEmptyObject()) {
            qlarrVariables[code] = componentVariables
        }
    }

    private fun writeDependentFunctions(componentCode: String, reservedCode: ReservedCode): List<List<String>> {
        return getDependents(componentCode, reservedCode).map {
            listOf(it.componentCode, it.instructionCode)
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