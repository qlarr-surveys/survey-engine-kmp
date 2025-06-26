package com.qlarr.surveyengine.context.assemble

import com.qlarr.surveyengine.dependency.DependencyMapper
import com.qlarr.surveyengine.dependency.ForwardDependencyAnalyzer
import com.qlarr.surveyengine.dependency.componentIndices
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.exposed.ReturnType
import com.qlarr.surveyengine.scriptengine.ScriptEngineValidate
import com.qlarr.surveyengine.scriptengine.ScriptValidationInput
import com.qlarr.surveyengine.scriptengine.ScriptValidationOutput
import com.qlarr.surveyengine.scriptengine.ValidationScriptError
import com.qlarr.surveyengine.validation.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.putJsonArray

internal class ContextBuilder(
    val components: MutableList<SurveyComponent> = mutableListOf(),
    val scriptEngine: ScriptEngineValidate

) {
    val sanitizedNestedComponents: List<ChildlessComponent> get() = components.sanitizedNestedComponents()
    lateinit var componentIndexList: List<ComponentIndex>
    private lateinit var validatedSystemInstructions: MutableList<ComponentInstruction>
    lateinit var skipMap: Map<String, List<NotSkippedInstructionManifesto>>

    init {
        val newComponents = components.map { it.clearErrors() }
        components.apply {
            clear()
            addAll(newComponents)
        }
    }

    fun validate(validateSpecialTypeGroups: Boolean = false) {
        val script = getValidationScript(validateSpecialTypeGroups)
        val items = buildJsonArray {
            script.forEach { validationInput ->
                addJsonObject {
                    validationInput.componentInstruction.instruction.run {
                        put(
                            "script",
                            JsonPrimitive(if (returnType == ReturnType.STRING && !isActive) "\"$text\"" else text)
                        )
                    }
                    putJsonArray("allowedVariables") {
                        validationInput.dependencies.forEach { add(JsonPrimitive(it)) }
                    }
                }
            }
        }
        val result = scriptEngine.validate(
            items.toString()
        )
        val processed: List<List<ValidationScriptError>> = try {
            jsonMapper.decodeFromString(result)
        } catch (e: Exception) {
            listOf() // Handle parsing issues gracefully
        }

        // Map results back to the expected output
        val scriptOutput = script.mapIndexed { index, scriptValidationInput ->
            ScriptValidationOutput(scriptValidationInput.componentInstruction, processed[index])
        }
        processScriptResult(scriptOutput)
    }

    private fun getValidationScript(validateSpecialTypeGroups: Boolean = false): List<ScriptValidationInput> {
        val newComponents = if (validateSpecialTypeGroups)
            components.validateDuplicates().validateEmptyParents().validateSpecialTypeGroups()
        else
            components.validateDuplicates().validateEmptyParents()

        components.apply {
            clear()
            addAll(newComponents)
        }
        components.forEachIndexed { index, surveyComponent ->
            val newComponent = surveyComponent.validateInstructions()
            components[index] = newComponent
        }
        components.apply {
            addStateToAllComponents()
        }

        components.forEachIndexed { index, surveyComponent ->
            val newComponent = surveyComponent.validateReferences(sanitizedNestedComponents)
            components[index] = newComponent
        }
        val dependencyMapper = DependencyMapper(sanitizedNestedComponents)

        ForwardDependencyAnalyzer(
            components, dependencyMapper.dependencyMap
        ).validateForwardDependencies()
            .validateSkipDestinations()
        componentIndexList = components.componentIndices()

        sanitizedNestedComponents.let { sanitisedComponents ->
            val systemInstructions = sanitisedComponents.map { childlessComponent ->
                childlessComponent.instructionList
                    .filter {
                        it is Instruction.State && it.reservedCode.requiresValidation && !it.generated &&
                                (it.isActive || (it.reservedCode != ReservedCode.Value && it.text.isNotEmpty()))
                    }.map { instruction ->
                        ComponentInstruction(
                            childlessComponent.code,
                            (instruction as Instruction.State).runnableInstruction()
                        )
                    }
            }.flatten()
            return systemInstructions.map { instruction ->
                ScriptValidationInput(
                    componentInstruction = instruction,
                    dependencies = dependencyMapper.dependencyMap[Dependent(
                        instruction.componentCode,
                        instruction.instruction.code
                    )]?.map {
                        it.toValueKey()
                    } ?: listOf(),
                )
            }
        }

    }

    private fun processScriptResult(scriptResult: List<ScriptValidationOutput>) {
        validatedSystemInstructions = scriptResult.filter {
            it.result.isNotEmpty()
        }.map {
            val errors = it.componentInstruction.instruction.errors.toMutableList().apply {
                addAll(it.result.map { it.toBindingError() })
            }
            it.componentInstruction.copy(
                instruction = it.componentInstruction.instruction.copy(errors = errors)
            )
        }.toMutableList()
        components.replaceInstruction("")
        components.apply {
            addPreviousNextInstruction()
            addPrioritisedInstruction("")
            skipMap = addNotSkippedInstructions(componentIndexList)
            addModeRelevanceInstruction()
            addParentRelevanceInstruction()
            adjustRelevanceInstruction()
            addValidityInstructions()
        }
    }

    private fun MutableList<SurveyComponent>.replaceInstruction(parentCode: String) {
        if (validatedSystemInstructions.isEmpty()) {
            return
        }
        forEachIndexed { index, surveyComponent ->
            var newComponent = surveyComponent.duplicate()
            val code = newComponent.uniqueCode(parentCode)
            validatedSystemInstructions.filter { it.componentCode == code }.forEach {
                newComponent = newComponent.withValidatedInstruction(it.instruction)
                validatedSystemInstructions.remove(it)
            }
            val newChildren = surveyComponent.children.toMutableList()
            newChildren.replaceInstruction(code)
            set(index, newComponent.duplicate(children = newChildren))
        }
    }

    private fun List<SurveyComponent>.sanitizedNestedComponents(parentCode: String = ""): List<ChildlessComponent> {
        val returnList = mutableListOf<ChildlessComponent>()
        filter { surveyComponent ->
            surveyComponent.noErrors()
        }.forEach { surveyComponent ->
            val newInstructions = surveyComponent.instructionList.filterNoErrors()
            returnList.add(
                surveyComponent.duplicate(instructionList = newInstructions).withParentCode(parentCode)
                    .toChildlessComponent(parentCode)
            )
            val newCode = surveyComponent.uniqueCode(parentCode)
            returnList.addAll(surveyComponent.children.sanitizedNestedComponents(newCode))
        }
        return returnList
    }
}