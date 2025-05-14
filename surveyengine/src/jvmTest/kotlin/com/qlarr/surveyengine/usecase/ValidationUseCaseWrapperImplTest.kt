package com.qlarr.surveyengine.usecase

import com.qlarr.scriptengine.getValidate
import com.qlarr.surveyengine.common.loadFromResources
import com.qlarr.surveyengine.model.jsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals

class ValidationUseCaseWrapperImplTest {

    @Test
    fun validate() {
        val x = loadFromResources("2.json")
        println(x)
        val stringOutput = ValidationUseCaseWrapper.create(getValidate(), loadFromResources("1.json")).validate()
        val jsonOutput: ValidationJsonOutput =
            jsonMapper.decodeFromString(ValidationJsonOutput.serializer(), stringOutput)
        val stringified: String = jsonMapper.encodeToString(ValidationJsonOutput.serializer(), jsonOutput)
        val deserialised: ValidationJsonOutput =
            jsonMapper.decodeFromString(ValidationJsonOutput.serializer(), stringified)
        assertEquals(jsonOutput, deserialised)
    }
}