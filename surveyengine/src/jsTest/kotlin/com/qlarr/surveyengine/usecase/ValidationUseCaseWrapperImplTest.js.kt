package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.model.jsonMapper
import kotlin.js.Json
import kotlin.test.Test
import kotlin.test.assertEquals

// Mirrors jvmTest ValidationUseCaseWrapperImplTest. The JVM test reads surveyDesign.json off the
// classpath; on JS we import it as a module (webpack/node resolves it from the processed test
// resources) and stringify it back to feed the wrapper.
@JsModule("./surveyDesign.json")
@JsNonModule
external val surveyDesignData: Json

class ValidationUseCaseWrapperImplTest {

    @Test
    fun validate() {
        val stringOutput = ValidationUseCaseWrapper.create(JSON.stringify(surveyDesignData)).validate()
        val jsonOutput: ValidationJsonOutput =
            jsonMapper.decodeFromString(ValidationJsonOutput.serializer(), stringOutput)
        val stringified: String = jsonMapper.encodeToString(ValidationJsonOutput.serializer(), jsonOutput)
        val deserialised: ValidationJsonOutput =
            jsonMapper.decodeFromString(ValidationJsonOutput.serializer(), stringified)
        assertEquals(jsonOutput.survey, deserialised.survey)
        assertEquals(jsonOutput.script, deserialised.script)
    }
}
