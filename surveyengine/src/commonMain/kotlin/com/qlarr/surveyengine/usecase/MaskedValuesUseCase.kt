package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.context.execute.ContextExecutor
import com.qlarr.surveyengine.context.execute.randomize
import com.qlarr.surveyengine.context.execute.sanitize
import com.qlarr.surveyengine.context.instructionsMap
import com.qlarr.surveyengine.context.nestedComponents
import com.qlarr.surveyengine.dependency.DependencyMapper
import com.qlarr.surveyengine.ext.*
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.navigate.allInOne
import com.qlarr.surveyengine.navigate.componentsInCurrentNav
import com.qlarr.surveyengine.scriptengine.ScriptEngineNavigate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject


@Suppress("unused")
class MaskedValuesUseCase(
    processedSurvey: String,
) {
    private val validationJsonOutput: ValidationJsonOutput =
        jsonMapper.decodeFromString<ValidationJsonOutput>(processedSurvey)
    private val validationOutput: ValidationOutput = validationJsonOutput.toValidationOutput()
    private val defaultLang = validationJsonOutput.survey.defaultLang()
    val lang = defaultLang
    private var survey = validationOutput.survey.sanitize()
        .replaceOrAddInstruction(Instruction.SimpleState(lang, ReservedCode.Lang)) as Survey
    private val surveyJson = validationJsonOutput.survey
    private val contextExecutor = ContextExecutor()

    fun navigate(scriptEngine: ScriptEngineNavigate, useCaseValues: String): Map<String, Any> {
        val script = getNavigationScript(useCaseValues)
        val scriptResult = scriptEngine.navigate(script)
        return processNavigationResult(scriptResult)
    }

    fun getNavigationScript(useCaseValues: String): String {
        val jsonValues = jsonMapper.parseToJsonElement(useCaseValues).jsonObject
        val values = jsonValues.withDependencyKeys()
        val startupRandomValues = mutableMapOf<Dependency, Int>()
        val dependencyMapper = DependencyMapper(validationOutput.impactMap)
        val alphaSorted = mutableMapOf<Dependency, Int>()

        alphaSorted.putAll(survey.randomize(listOf(Instruction.RandomOption.ALPHA)) { getLabel(it) })
        val labelsMap = survey.getLabels(surveyJson, "", lang, defaultLang, dependencyMapper.impactMap)
        val valueBindings =
            values.toMutableMap().apply {
                putAll(startupRandomValues.mapValues { JsonPrimitive(it.value) })
                putAll(alphaSorted.mapValues { JsonPrimitive(it.value) })
                putAll(labelsMap.mapValues { JsonPrimitive(it.value) })
            }

        val instructionsMap = listOf(survey).instructionsMap()

        // We want to assume that the survey is all shown, to get a good feeling of what is valid and what is not
        survey.componentsInCurrentNav(survey.allInOne()).forEach {
            valueBindings[Dependency(it, ReservedCode.InCurrentNavigation)] = JsonPrimitive(true)
        }

        val sequence = instructionsMap.filterValues { !it.isActive }.keys.toMutableList()
            .apply {
                addAll(
                    instructionsMap.filterKeys { it.reservedCode == ReservedCode.MaskedValue }.keys.toList()
                )
            }

        val referenceInstructions = survey.nestedComponents().map { childlessComponent ->
            childlessComponent.instructionList
                .filterIsInstance<Instruction.Reference>()
                .map { instruction ->
                    ComponentInstruction(childlessComponent.code, instruction.runnableInstruction())
                }
        }.flatten()
        return contextExecutor.getNavigationScript(instructionsMap, valueBindings, sequence, referenceInstructions)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun processNavigationResult(scriptResult: String): Map<String, Any> {
        val valuesMap = contextExecutor.processNavigationValues(scriptResult)
        return valuesMap.first.filterKeys { it.reservedCode == ReservedCode.MaskedValue }.mapKeys { it.key.asCode() }

    }

    private fun getLabel(qualifiedCode: String): String {
        val codes = qualifiedCode.splitToComponentCodes().toMutableList()
        if (codes[0].isQuestionCode()) {
            val groupCode = survey.groups.first { group -> group.questions.map { it.code }.contains(codes[0]) }.code
            codes.add(0, groupCode)
        }
        return surveyJson.getChild(codes).getLabel(lang, defaultLang)
    }

}


