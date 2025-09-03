package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.ext.copyReducedToJSON
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.exposed.NavigationDirection
import com.qlarr.surveyengine.model.exposed.NavigationIndex
import com.qlarr.surveyengine.model.exposed.NavigationMode
import com.qlarr.surveyengine.model.exposed.SurveyMode
import com.qlarr.surveyengine.scriptengine.ScriptEngineNavigate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal class NavigationUseCaseWrapperImpl(
    private val lang: String? = null,
    processedSurvey: String,
    values: Map<String, JsonElement> = mapOf(),
    navigationMode: NavigationMode,
    val navigationIndex: NavigationIndex? = null,
    val navigationDirection: NavigationDirection = NavigationDirection.Start,
    skipInvalid: Boolean,
    surveyMode: SurveyMode
) : NavigationUseCaseWrapper {

    private val validationJsonOutput: ValidationJsonOutput =
        jsonMapper.decodeFromString<ValidationJsonOutput>(processedSurvey)
    private val validationOutput: ValidationOutput = validationJsonOutput.toValidationOutput()

    private val useCase = NavigationUseCaseImp(
        validationOutput,
        validationJsonOutput.survey,
        values,
        navigationIndex, navigationDirection,
        navigationMode = navigationMode,
        lang ?: validationJsonOutput.survey.defaultLang(),
        skipInvalid,
        surveyMode
    )

    override fun navigate(scriptEngine: ScriptEngineNavigate): String {
        if (validationOutput.survey.hasErrors()) {
            throw SurveyDesignWithErrorException
        }
        val navigationOutput = useCase.navigate(scriptEngine)
        return jsonMapper.encodeToString(NavigationJsonOutput.serializer(), processNavigationOutput(navigationOutput))
    }

    override fun getNavigationScript() = useCase.getNavigationScript()

    override fun processNavigationResult(scriptResult: String): String {
        val navigationOutput = useCase.processNavigationResult(scriptResult)
        return jsonMapper.encodeToString(NavigationJsonOutput.serializer(), processNavigationOutput(navigationOutput))
    }

    private fun processNavigationOutput(navigationOutput: NavigationOutput): NavigationJsonOutput {
        val state = StateMachineWriter(navigationOutput.toScriptInput()).state()
        return navigationOutput.toNavigationJsonOutput(
            surveyJson = validationJsonOutput.survey, state = state,
            lang = lang
        )
    }

}


data class ScriptInput(
    val contextComponents: List<ChildlessComponent>,
    val bindings: Map<Dependency, JsonElement>,
    val dependencyMapBundle: DependencyMapBundle,
    val formatBindings: Map<Dependent, JsonElement>,
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