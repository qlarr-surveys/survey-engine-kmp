package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.scriptengine.ScriptEngineCalculate
import com.qlarr.surveyengine.scriptengine.getValidate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject

internal class CalculationUseCaseWrapperImpl(processedSurvey: String) : CalculationUseCaseWrapper {
    private val useCase = CalculationUseCase(
        getValidate(),
        jsonMapper.decodeFromString<ValidationJsonOutput>(processedSurvey).toValidationOutput().survey
    )

    override fun validate(calculations: String): String {
        val validated = useCase.validate(jsonMapper.decodeFromString(calculations))
        return jsonMapper.encodeToString(ListSerializer(Calculation.serializer()), validated)
    }

    override fun calculate(scriptEngine: ScriptEngineCalculate, calculations: String, responses: String): String {
        val output = useCase.calculate(
            scriptEngine,
            jsonMapper.decodeFromString(calculations),
            jsonMapper.decodeFromString<Map<String, JsonObject>>(responses)
        )
        return jsonMapper.encodeToString(MapSerializer(String.serializer(), CalculationOutput.serializer()), output)
    }
}
