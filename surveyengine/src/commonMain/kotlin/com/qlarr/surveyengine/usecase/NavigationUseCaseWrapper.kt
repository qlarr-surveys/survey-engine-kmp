package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.ext.copyReducedToJSON
import com.qlarr.surveyengine.model.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject


interface NavigationUseCaseWrapper {
    // Serialized NavigationJsonOutput
    fun navigate(): String
    fun getNavigationScript(): String
    fun processNavigationResult(scriptResult: String): String

    companion object {
        fun init(
            scriptEngine: ScriptEngineNavigate,
            processedSurvey: String,
            values: Map<String, Any> = mapOf(),
            lang: String? = null,
            navigationMode: NavigationMode? = null,
            navigationInfo: NavigationInfo = NavigationInfo(),
            skipInvalid: Boolean,
            surveyMode: SurveyMode
        ): NavigationUseCaseWrapper = NavigationUseCaseWrapperImpl(
            scriptEngine = scriptEngine,
            processedSurvey = processedSurvey,
            skipInvalid = skipInvalid,
            surveyMode = surveyMode,
            values = values,
            lang = lang,
            navigationMode = navigationMode,
            navigationInfo = navigationInfo
        )
    }
}

internal class NavigationUseCaseWrapperImpl(
    private val scriptEngine: ScriptEngineNavigate,
    private val lang: String? = null,
    processedSurvey: String,
    values: Map<String, @Contextual Any> = mapOf(),
    navigationMode: NavigationMode? = null,
    navigationInfo: NavigationInfo = NavigationInfo(),
    skipInvalid: Boolean,
    surveyMode: SurveyMode
) : NavigationUseCaseWrapper {

    private val validationJsonOutput: ValidationJsonOutput = jsonMapper.decodeFromString<ValidationJsonOutput>(processedSurvey)
    private val validationOutput: ValidationOutput = validationJsonOutput.toValidationOutput()

    private val useCase = NavigationUseCaseImp(
        validationOutput,
        validationJsonOutput.survey,
        values,
        navigationInfo,
        navigationMode = when (navigationInfo.navigationIndex) {
            is NavigationIndex.Group -> NavigationMode.GROUP_BY_GROUP
            is NavigationIndex.Groups -> NavigationMode.ALL_IN_ONE
            is NavigationIndex.Question -> NavigationMode.QUESTION_BY_QUESTION
            is NavigationIndex.End -> null
            null -> null
        } ?: navigationMode ?: validationJsonOutput.surveyNavigationData().navigationMode,
        lang ?: validationJsonOutput.survey.defaultLang(),
        skipInvalid,
        surveyMode
    )

    override fun navigate(): String {
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
    val bindings: Map<Dependency, Any>,
    val dependencyMapBundle: DependencyMapBundle,
    val formatBindings: Map<Dependent, Any>,
)

@Serializable
internal data class NavigationJsonOutput(
    val survey: JsonObject = buildJsonObject {},
    val state: JsonObject = buildJsonObject {},
    val navigationIndex: NavigationIndex,
    val toSave: Map<String, @Contextual Any> = mapOf()
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