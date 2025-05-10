package com.qlarr.surveyengine.context.execute

import com.qlarr.surveyengine.ext.jsonValueToObject
import com.qlarr.surveyengine.model.*
import kotlinx.serialization.json.*

internal class ContextExecutor {
    fun processNavigationValues(navigationValues: String): Pair<MutableMap<Dependency, Any>, Map<Dependent, Any>> {
        val jsonElement = Json.parseToJsonElement(navigationValues)
        val jsonObject = jsonElement.jsonObject
        val stateValues = mutableMapOf<Dependency, Any>()
        val formatValues = mutableMapOf<Dependent, Any>()
        
        jsonObject.entries.forEach { (componentName, componentJson) ->
            val vars = componentJson.jsonObject
            vars.entries.forEach { (key, value) ->
                if (key.isReservedCode()) {
                    stateValues[Dependency(componentName, key.toReservedCode())] = jsonValueToObject(value)
                } else {
                    formatValues[Dependent(componentName, key)] = jsonValueToObject(value)
                }
            }
        }
        
        return Pair(stateValues, formatValues)
    }

    fun getNavigationScript(
        instructionsMap: LinkedHashMap<Dependency, Instruction.State>,
        valueBindings: Map<Dependency, Any>,
        sequence: List<Dependency>,
        formatInstructions: List<ComponentInstruction>
    ): String {
        val values = valueBindings
            .filterKeys { instructionsMap.containsKey(it) }
            .mapValues {
                TypedValue(
                    value = it.value,
                    returnType = instructionsMap[it.key]!!.returnType
                )
            }
        val navSequence = sequence.map {
            ComponentInstruction(it.componentCode, instructionsMap[it]!!.runnableInstruction())
        }
        val codes = values.keys.map { it.componentCode }.toMutableSet().apply {
            addAll(navSequence.map { it.componentCode })
            addAll(formatInstructions.map { it.componentCode })
        }
        val instructionNavigationInput = NavigationInstructionsInput(
            values = values.mapKeys { it.key.toValueKey() },
            sequence = navSequence,
            formatInstructions = formatInstructions,
            codes = codes.toList()
        )
        return kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.serializer<NavigationInstructionsInput>(), 
            instructionNavigationInput
        )
    }
}

@kotlinx.serialization.Serializable
data class NavigationInstructionsInput(
    val values: Map<String, TypedValue>,
    val sequence: List<ComponentInstruction>,
    val formatInstructions: List<ComponentInstruction>,
    val codes: List<String>
)