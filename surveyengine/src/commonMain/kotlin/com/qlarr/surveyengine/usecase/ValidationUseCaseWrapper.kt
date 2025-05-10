package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.ext.copyErrorsToJSON
import com.qlarr.surveyengine.model.Survey
import com.qlarr.surveyengine.model.jsonMapper
import kotlinx.serialization.json.jsonObject

interface ValidationUseCaseWrapper {
    fun validate(): ValidationJsonOutput
}

class ValidationUseCaseWrapperImpl(scriptEngine: ScriptEngineValidate, private val surveyJson: String) :
    ValidationUseCaseWrapper {
    private val useCase: ValidationUseCase

    init {
        val survey = jsonMapper.decodeFromString<Survey>(surveyJson)
        useCase = ValidationUseCaseImpl(scriptEngine, survey)
    }


    override fun validate(): ValidationJsonOutput {
        val output = useCase.validate()
        val survey = jsonMapper.parseToJsonElement(surveyJson).jsonObject
        return ValidationJsonOutput(
            survey = output.survey.copyErrorsToJSON(survey),
            schema = output.schema,
            impactMap = output.impactMap,
            componentIndexList = output.componentIndexList,
            skipMap = output.skipMap,
            script = output.script
        )
    }
}
