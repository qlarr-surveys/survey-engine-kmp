package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.ext.copyComponentsToJson
import com.qlarr.surveyengine.model.Survey
import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.scriptengine.getValidate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class ValidationUseCaseWrapperImpl(private val surveyJson: String) :
    ValidationUseCaseWrapper {
    private val useCase: ValidationUseCase

    init {
        val survey = jsonMapper.decodeFromString<Survey>(surveyJson)
        val quotaCodes = (jsonMapper.parseToJsonElement(surveyJson).jsonObject["quotas"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.get("code")?.jsonPrimitive?.contentOrNull }
            ?: listOf()
        useCase = ValidationUseCaseImpl(getValidate(), survey, quotaCodes)
    }


    override fun validate(): String {
        val output = useCase.validate()
        val survey = jsonMapper.parseToJsonElement(surveyJson).jsonObject
        return jsonMapper.encodeToString(
            ValidationJsonOutput.serializer(), ValidationJsonOutput(
                survey = output.survey.copyComponentsToJson(survey),
                schema = output.schema,
                replacements = output.replacements,
                impactMap = output.impactMap,
                componentIndexList = output.componentIndexList,
                skipMap = output.skipMap,
                script = output.script
            )
        )
    }
}