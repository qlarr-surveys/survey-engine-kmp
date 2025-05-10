package com.qlarr.surveyengine.usecase

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import com.qlarr.surveyengine.ext.copyReducedToJSON
import com.qlarr.surveyengine.model.*

interface NavigationUseCaseWrapper {
    fun navigate(scriptEngine: ScriptEngineNavigate): NavigationJsonOutput
    fun getNavigationScript(): String
    fun processNavigationResult(scriptResult: String): NavigationJsonOutput
}

class NavigationUseCaseWrapperImpl(
    private val validationJsonOutput: ValidationJsonOutput,
    private val useCaseInput: NavigationUseCaseInput,
    skipInvalid: Boolean,
    surveyMode: SurveyMode
) : NavigationUseCaseWrapper {

    private val validationOutput: ValidationOutput = validationJsonOutput.toValidationOutput()

    private val useCase = NavigationUseCaseImp(
        validationOutput,
        validationJsonOutput.survey,
        useCaseInput.values,
        useCaseInput.navigationInfo,
        navigationMode = when (useCaseInput.navigationInfo.navigationIndex) {
            is NavigationIndex.Group -> NavigationMode.GROUP_BY_GROUP
            is NavigationIndex.Groups -> NavigationMode.ALL_IN_ONE
            is NavigationIndex.Question -> NavigationMode.QUESTION_BY_QUESTION
            is NavigationIndex.End -> null
            null -> null
        } ?: useCaseInput.navigationMode ?: validationJsonOutput.surveyNavigationData().navigationMode,
        useCaseInput.lang ?: validationJsonOutput.survey.defaultLang(),
        skipInvalid,
        surveyMode
    )

    override fun navigate(scriptEngine: ScriptEngineNavigate): NavigationJsonOutput {
        if (validationOutput.survey.hasErrors()) {
            throw SurveyDesignWithErrorException
        }
        val navigationOutput = useCase.navigate(scriptEngine)
        return processNavigationOutput(navigationOutput)
    }

    override fun getNavigationScript() = useCase.getNavigationScript()

    override fun processNavigationResult(scriptResult: String): NavigationJsonOutput {
        val navigationOutput = useCase.processNavigationResult(scriptResult)
        return processNavigationOutput(navigationOutput)
    }

    private fun processNavigationOutput(navigationOutput: NavigationOutput): NavigationJsonOutput {
        val state = StateMachineWriter(navigationOutput.toScriptInput()).state()
        return navigationOutput.toNavigationJsonOutput(
            surveyJson = validationJsonOutput.survey, state = state,
            lang = useCaseInput.lang
        )
    }

}


data class ScriptInput(
    val contextComponents: List<ChildlessComponent>,
    val bindings: Map<Dependency, Any>,
    val dependencyMapBundle: DependencyMapBundle,
    val formatBindings: Map<Dependent, Any>,
)

data class NavigationJsonOutput(
    val survey: JsonObject = buildJsonObject {},
    val state: JsonObject = buildJsonObject {},
    val navigationIndex: NavigationIndex,
    val toSave: Map<String, Any> = mapOf()
)

private fun NavigationOutput.toScriptInput(): ScriptInput {
    return ScriptInput(
        contextComponents = contextComponents,
        bindings = stateBindings,
        dependencyMapBundle = dependencyMapBundle,
        formatBindings = formatBindings
    )
}

private fun NavigationOutput.toNavigationJsonOutput(
    state: JsonObject, surveyJson: JsonObject, lang: String?,
): NavigationJsonOutput {
    return NavigationJsonOutput(
        state = state,
        toSave = toSave.withStringKeys(),
        survey = surveyJson.copyReducedToJSON(orderedSurvey, reducedSurvey, lang, surveyJson.defaultLang()),
        navigationIndex = navigationIndex,
    )
}

object SurveyDesignWithErrorException : Exception()