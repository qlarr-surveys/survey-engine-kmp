package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.ext.copyComponentsToJson
import com.qlarr.surveyengine.ext.copyDeserializationIssues
import com.qlarr.surveyengine.model.DeserializationIssue
import com.qlarr.surveyengine.model.TolerantSurveyComponentSerializer
import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.scriptengine.getValidate
import kotlinx.serialization.json.jsonObject

internal class ValidationUseCaseWrapperImpl(private val surveyJson: String) :
    ValidationUseCaseWrapper {
    private val useCase: ValidationUseCase
    private val deserializationIssues: List<DeserializationIssue>

    init {
        val result = TolerantSurveyComponentSerializer.deserializeTolerant(surveyJson)
        useCase = ValidationUseCaseImpl(getValidate(), result.survey)
        deserializationIssues = result.issues
    }


    override fun validate(): String {
        val output = useCase.validate()
        val survey = jsonMapper.parseToJsonElement(surveyJson).jsonObject

        // First copy components (errors, instructions, etc.), then copy deserialization issues
        val surveyWithComponents = output.survey.copyComponentsToJson(survey)
        val surveyWithIssues = surveyWithComponents.copyDeserializationIssues(deserializationIssues)

        return jsonMapper.encodeToString(
            ValidationJsonOutput.serializer(), ValidationJsonOutput(
                survey = surveyWithIssues,
                schema = output.schema,
                impactMap = output.impactMap,
                componentIndexList = output.componentIndexList,
                skipMap = output.skipMap,
                script = output.script
            )
        )
    }
}