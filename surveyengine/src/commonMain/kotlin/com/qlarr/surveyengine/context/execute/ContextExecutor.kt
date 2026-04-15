package com.qlarr.surveyengine.context.execute

import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.exposed.TypedValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

internal class ContextExecutor {
    fun processNavigationValues(navigationValues: String): Pair<MutableMap<Dependency, JsonElement>, Map<Dependent, JsonElement>> {
        val jsonElement = Json.parseToJsonElement(navigationValues)
        val jsonObject = jsonElement.jsonObject
        val stateValues = mutableMapOf<Dependency, JsonElement>()
        val formatValues = mutableMapOf<Dependent, JsonElement>()
        
        jsonObject.entries.forEach { (componentName, componentJson) ->
            val vars = componentJson.jsonObject
            vars.entries.forEach { (key, value) ->
                if (key.isReservedCode()) {
                    stateValues[Dependency(componentName, key.toReservedCode())] = value
                } else {
                    formatValues[Dependent(componentName, key)] = value
                }
            }
        }
        
        return Pair(stateValues, formatValues)
    }

    fun getNavigationScript(
        instructionsMap: LinkedHashMap<Dependency, Instruction.State>,
        valueBindings: Map<Dependency, JsonElement>,
        replacements: Map<String, String>,
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
            val runnableInstruction = instructionsMap[it]!!.runnableInstruction()
            val key = it.componentCode + "." + runnableInstruction.code
            val finalRunnable = if (replacements.containsKey(key)) {
                runnableInstruction.copy(text = replacements[key]!!)
            } else {
                runnableInstruction
            }
            ComponentInstruction(it.componentCode, finalRunnable)
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
        return Json.encodeToString(
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