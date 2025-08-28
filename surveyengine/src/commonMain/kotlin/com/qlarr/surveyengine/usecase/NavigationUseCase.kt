package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.context.execute.*
import com.qlarr.surveyengine.context.instructionsMap
import com.qlarr.surveyengine.context.nestedComponents
import com.qlarr.surveyengine.dependency.DependencyMapper
import com.qlarr.surveyengine.dependency.toDependent
import com.qlarr.surveyengine.ext.*
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.Instruction.RandomOption
import com.qlarr.surveyengine.model.ReservedCode.Order
import com.qlarr.surveyengine.model.ReservedCode.Priority
import com.qlarr.surveyengine.model.exposed.*
import com.qlarr.surveyengine.navigate.*
import com.qlarr.surveyengine.scriptengine.ScriptEngineNavigate
import kotlinx.serialization.json.*
import kotlin.collections.flatten

interface NavigationUseCase {
    fun navigate(scriptEngine: ScriptEngineNavigate): NavigationOutput
    fun getNavigationScript(): String
    fun processNavigationResult(scriptResult: String): NavigationOutput
}

class NavigationUseCaseImp(
    private val validationOutput: ValidationOutput,
    private val surveyJson: JsonObject,
    stringValues: Map<String, JsonElement> = mapOf(),
    private val navigationIndex: NavigationIndex? = null,
    private val navigationDirection: NavigationDirection,
    private val navigationMode: NavigationMode,
    private val lang: String,
    private val skipInvalid: Boolean,
    private val surveyMode: SurveyMode
) : NavigationUseCase {
    private val values = stringValues.withDependencyKeys(validationOutput.schema)
    private val startupRandomValues = mutableMapOf<Dependency, Int>()
    private val contextExecutor = ContextExecutor()
    private var survey = validationOutput.survey.sanitize()
        .replaceOrAddInstruction(Instruction.SimpleState(lang, ReservedCode.Lang)) as Survey
    private val dependencyMapper = DependencyMapper(validationOutput.impactMap)
    private val skipMap = validationOutput.skipMap
    private lateinit var contextRunner: ContextRunner


    override fun navigate(scriptEngine: ScriptEngineNavigate): NavigationOutput {
        val script = getNavigationScript()
        val scriptResult = scriptEngine.navigate(script)
        return processNavigationResult(scriptResult)
    }

    override fun getNavigationScript(): String {
        val alphaSorted = mutableMapOf<Dependency, Int>()
        if (navigationDirection == NavigationDirection.Start) {
            startupRandomValues.putAll(survey.randomize(RandomOption.entries) { getLabel(it) })
            startupRandomValues.putAll(survey.setPriorities())
        } else {
            alphaSorted.putAll(survey.randomize(listOf(RandomOption.ALPHA)) { getLabel(it) })
        }
        val labelsMap = survey.getLabels(surveyJson, "", lang, surveyJson.defaultLang(), dependencyMapper.impactMap)
        val valueBindings =
            values.toMutableMap().apply {
                putAll(startupRandomValues.mapValues { JsonPrimitive(it.value) })
                putAll(alphaSorted.mapValues { JsonPrimitive(it.value) })
                putAll(labelsMap.mapValues { JsonPrimitive(it.value) })
                put(modeDependency, JsonPrimitive(surveyMode.name.lowercase()))
            }

        val instructionsMap = listOf(survey).instructionsMap()
        val orderPriorityValues = valueBindings.filterKeys {
            it.reservedCode in listOf(
                Order,
                Priority
            )
        }.mapValues { it.value.jsonPrimitive.int }
        contextRunner = ContextRunner(
            instructionsMap,
            dependencyMapper.dependencyMap,
            orderPriorityValues,
            skipMap
        )
        // We want to assume that the survey is all shown, to get a good feeling of what is valid and what is not
        survey.componentsInCurrentNav(survey.allInOne()).forEach {
            valueBindings[Dependency(it, ReservedCode.InCurrentNavigation)] = JsonPrimitive(true)
        }
        val sequence = contextRunner.instructionsRefreshSequence()
        val referenceInstructions = survey.nestedComponents().map { childlessComponent ->
            childlessComponent.instructionList
                .filterIsInstance<Instruction.Reference>()
                .map { instruction ->
                    ComponentInstruction(childlessComponent.code, instruction.runnableInstruction())
                }
        }.flatten()
        return contextExecutor.getNavigationScript(instructionsMap, valueBindings, sequence, referenceInstructions)
    }

    override fun processNavigationResult(scriptResult: String): NavigationOutput {
        val navDependencies = contextRunner.navigationDependencies()
        val valuesMap = contextExecutor.processNavigationValues(scriptResult)
        val stateBindings = valuesMap.first
        val formatBindings = valuesMap.second
        val navigationBindings = stateBindings.filterBindings(navDependencies)
        survey = survey.sortByOrder(stateBindings.filterKeys { it.reservedCode == Order })
        val extraBindings = mutableMapOf<Dependency, JsonElement>()
        val runtimeContextBuilder = RuntimeContextBuilder(
            stateBindings,
            dependencyMapper.impactMap.toMutableMap(),
            dependencyMapper.dependencyMap.toMutableMap()
        )
        val currentIndexValidity: Boolean = runtimeContextBuilder.addValidityInstruction(
            survey,
            navigationIndex ?: survey.allInOne()
        )[Dependency(
            "Survey",
            ReservedCode.Validity
        )]!!.jsonPrimitive.boolean
        val newNavIndex =
            survey.navigate(
                navigationIndex,
                navigationDirection,
                navigationMode,
                navigationBindings,
                skipInvalid,
                currentIndexValidity
            )

        extraBindings.putAll(runtimeContextBuilder.addShowErrorsInstruction(survey, !newNavIndex.showError))
        extraBindings.putAll(runtimeContextBuilder.addValidityInstruction(survey, newNavIndex))
        extraBindings.putAll(runtimeContextBuilder.addBeforeAfterNav(survey, newNavIndex))
        val contextComponent = survey.nestedComponents()
        val reducedSurvey = survey.reduce(newNavIndex)
        val bindings = stateBindings.toMutableMap().apply {
            putAll(extraBindings)
        }
        val dependenciesToSave = validationOutput.schema.filter {
            if (navigationDirection == NavigationDirection.Start)
                it.columnName == ColumnName.ORDER || it.columnName == ColumnName.PRIORITY || values.keys.contains(
                    it.toDependency()
                )
            else
                values.keys.contains(it.toDependency())
        }.map { it.toDependency() }.toSet()

        val toSave: Map<Dependent, JsonElement> = formatBindings + stateBindings.apply {
            putAll(startupRandomValues.mapValues { JsonPrimitive(it.value) })
        }.filterStateToSave(dependenciesToSave)

        return NavigationOutput(
            reducedSurvey = reducedSurvey,
            orderedSurvey = survey,
            contextComponents = contextComponent,
            stateBindings = bindings,
            formatBindings = formatBindings,
            toSave = toSave,
            dependencyMapBundle = Pair(dependencyMapper.impactMap, dependencyMapper.dependencyMap),
            navigationIndex = newNavIndex
        )
    }

    private fun getLabel(qualifiedCode: String): String {
        val codes = qualifiedCode.splitToComponentCodes().toMutableList()
        if (codes[0].isQuestionCode()) {
            val groupCode = survey.groups.first { group -> group.questions.map { it.code }.contains(codes[0]) }.code
            codes.add(0, groupCode)
        }
        return surveyJson.getChild(codes).getLabel(lang, surveyJson.defaultLang())
    }
}

private fun Map<Dependency, JsonElement>.filterBindings(dependencies: Set<Dependency>): Map<Dependency, JsonElement> {
    return filterKeys { dependencies.contains(it) }
}

private fun Map<Dependency, JsonElement>.filterStateToSave(
    dependencies: Set<Dependency>
): Map<Dependent, JsonElement> {
    return filterKeys {
        when (it.reservedCode) {
            ReservedCode.AfterNavigation,
            ReservedCode.BeforeNavigation,
            ReservedCode.ChildrenRelevance,
            ReservedCode.ConditionalRelevance,
            ReservedCode.HasNext,
            ReservedCode.HasPrevious,
            ReservedCode.InCurrentNavigation,
            ReservedCode.Label,
            ReservedCode.Lang,
            ReservedCode.Meta,
            ReservedCode.ModeRelevance,
            ReservedCode.NotSkipped,
            ReservedCode.Prioritised,
            ReservedCode.RelevanceMap,
            ReservedCode.ShowErrors,
            is ReservedCode.Skip,
            is ReservedCode.ValidationRule,
            ReservedCode.Validity,
            ReservedCode.ValidityMap -> false

            ReservedCode.Mode,
            ReservedCode.Disqualified,
            ReservedCode.Value -> true

            ReservedCode.Relevance -> !this[it]!!.jsonPrimitive.boolean
            ReservedCode.MaskedValue -> this[it] != this[Dependency(it.componentCode, ReservedCode.Value)]

            Order,
            Priority -> dependencies.contains(it)
        }
    }.mapKeys { it.key.toDependent() }
}


data class NavigationOutput(
    val orderedSurvey: Survey = Survey(),
    val reducedSurvey: Survey = Survey(),
    val contextComponents: List<ChildlessComponent> = listOf(),
    val stateBindings: Map<Dependency, JsonElement> = mapOf(),
    val toSave: Map<Dependent, JsonElement> = mapOf(),
    val dependencyMapBundle: DependencyMapBundle = DependencyMapBundle(mapOf(), mapOf()),
    val navigationIndex: NavigationIndex,
    val formatBindings: Map<Dependent, JsonElement> = mapOf()
)