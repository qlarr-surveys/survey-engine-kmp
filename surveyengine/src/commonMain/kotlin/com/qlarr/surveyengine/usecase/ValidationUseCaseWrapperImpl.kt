package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.ext.copyComponentsToJson
import com.qlarr.surveyengine.model.Survey
import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.scriptengine.getValidate
import kotlinx.serialization.json.jsonObject

internal class ValidationUseCaseWrapperImpl(private val surveyJson: String) :
    ValidationUseCaseWrapper {
    private val useCase: ValidationUseCase

    init {
        val survey = jsonMapper.decodeFromString<Survey>(surveyJson)
        useCase = ValidationUseCaseImpl(getValidate(), survey)
    }


    override fun validate(): String {
        val output = useCase.validate()
        val survey = jsonMapper.parseToJsonElement(surveyJson).jsonObject
        return jsonMapper.encodeToString(
            ValidationJsonOutput.serializer(), ValidationJsonOutput(
                survey = output.survey.copyComponentsToJson(survey),
                schema = output.schema,
                impactMap = output.impactMap,
                componentIndexList = output.componentIndexList,
                skipMap = output.skipMap,
                script = output.script
            )
        )
    }
}