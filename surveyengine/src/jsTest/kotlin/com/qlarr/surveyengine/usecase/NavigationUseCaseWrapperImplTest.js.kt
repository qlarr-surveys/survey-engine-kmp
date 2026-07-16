package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.model.NavigationJsonOutput
import com.qlarr.surveyengine.model.exposed.NavigationDirection
import com.qlarr.surveyengine.model.exposed.NavigationMode
import com.qlarr.surveyengine.model.exposed.SurveyMode
import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.scriptengine.getNavigate
import kotlinx.serialization.json.jsonObject
import kotlin.js.Json
import kotlin.test.Test
import kotlin.test.assertTrue

// Mirrors jvmTest NavigationUseCaseWrapperImplTest. The JVM test reads validationJsonOutput.json off
// the classpath; on JS we import it as a module and stringify it back to feed the wrapper. getNavigate()
// here is the JS actual (compiles common+initial script via `new Function`), the counterpart of the
// GraalVM-backed JVM engine.
@JsModule("./validationJsonOutput.json")
@JsNonModule
external val processedSurveyData: Json

class NavigationUseCaseWrapperImplTest {

    @Test
    fun navigate() {
        val output = NavigationUseCaseWrapper.init(
            values = "{}",
            processedSurvey = JSON.stringify(processedSurveyData),
            skipInvalid = false,
            surveyMode = SurveyMode.ONLINE,
            navigationMode = NavigationMode.GROUP_BY_GROUP,
            navigationDirection = NavigationDirection.Start
        ).navigate(getNavigate())
        val deserialised: NavigationJsonOutput =
            jsonMapper.decodeFromString(NavigationJsonOutput.serializer(), output)
        assertTrue(deserialised.toSave.isNotEmpty())
        assertTrue(deserialised.state["qlarrVariables"]!!.jsonObject.isNotEmpty())
        assertTrue(deserialised.state["qlarrDependents"]!!.jsonObject.isNotEmpty())
    }
}
