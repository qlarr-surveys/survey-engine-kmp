package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.common.loadFromResources
import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.scriptengine.getValidate
import kotlin.test.Test
import kotlin.test.assertEquals

class ValidationUseCaseWrapperImplTest {

    @Test
    fun validate() {
        val stringOutput = ValidationUseCaseWrapper.create(getValidate(), loadFromResources("surveyDesign.json")).validate()
        val jsonOutput: ValidationJsonOutput =
            jsonMapper.decodeFromString(ValidationJsonOutput.serializer(), stringOutput)
        val stringified: String = jsonMapper.encodeToString(ValidationJsonOutput.serializer(), jsonOutput)
        val deserialised: ValidationJsonOutput =
            jsonMapper.decodeFromString(ValidationJsonOutput.serializer(), stringified)
        assertEquals(jsonOutput.survey, deserialised.survey)
        assertEquals(jsonOutput.script, deserialised.script)
    }
}