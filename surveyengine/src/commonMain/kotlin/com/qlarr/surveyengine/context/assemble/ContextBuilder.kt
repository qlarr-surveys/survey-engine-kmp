package com.qlarr.surveyengine.context.assemble

import com.qlarr.surveyengine.dependency.DependencyMapper
import com.qlarr.surveyengine.dependency.ForwardDependencyAnalyzer
import com.qlarr.surveyengine.dependency.componentIndices
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.usecase.ScriptEngineValidate
import com.qlarr.surveyengine.usecase.ScriptValidationInput
import com.qlarr.surveyengine.usecase.ScriptValidationOutput
import com.qlarr.surveyengine.validation.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer

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
        val result = scriptEngine.validate(
            jsonMapper.encodeToString(
                ListSerializer(serializer<ScriptValidationInput>()),
                script
            )
        )
        processScriptResult(jsonMapper.decodeFromString(ListSerializer(serializer<ScriptValidationOutput>()), result))
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
                        it is Instruction.State && it.reservedCode.requiresValidation &&
                                (it.isActive || it.reservedCode != ReservedCode.Value || it.text.isNotEmpty())
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