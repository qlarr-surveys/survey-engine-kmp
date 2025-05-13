package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.ext.copyReducedToJSON
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.exposed.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.json.JsonObject
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
interface NavigationUseCaseWrapper {
    // Serialized NavigationJsonOutput
    fun navigate(): String
    fun getNavigationScript(): String
    fun processNavigationResult(scriptResult: String): String

    companion object {
        fun init(
            scriptEngine: ScriptEngineNavigate,
            useCaseInput:String
        ): NavigationUseCaseWrapper {
            val input = jsonMapper.decodeFromString(NavigationUseCaseInput.serializer(), useCaseInput)
            return NavigationUseCaseWrapperImpl(
                scriptEngine = scriptEngine,
                processedSurvey = input.processedSurvey,
                skipInvalid = input.skipInvalid,
                surveyMode = input.surveyMode,
                values = input.values,
                lang = input.lang,
                navigationMode = input.navigationMode,
                navigationIndex = input.navigationIndex,
                navigationDirection = input.navigationDirection
            )
        }
    }
}

internal class NavigationUseCaseWrapperImpl(
    private val scriptEngine: ScriptEngineNavigate,
    private val lang: String? = null,
    processedSurvey: String,
    values: Map<String, @Contextual Any> = mapOf(),
    navigationMode: NavigationMode? = null,
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
        navigationMode = when (navigationIndex) {
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