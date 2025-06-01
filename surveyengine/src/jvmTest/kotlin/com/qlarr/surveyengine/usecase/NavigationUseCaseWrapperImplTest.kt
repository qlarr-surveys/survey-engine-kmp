package com.qlarr.surveyengine.usecase

import com.qlarr.scriptengine.getNavigate
import com.qlarr.surveyengine.common.loadFromResources
import com.qlarr.surveyengine.ext.engineScript
import com.qlarr.surveyengine.model.NavigationJsonOutput
import com.qlarr.surveyengine.model.exposed.NavigationDirection
import com.qlarr.surveyengine.model.exposed.NavigationMode
import com.qlarr.surveyengine.model.exposed.SurveyMode
import com.qlarr.surveyengine.model.jsonMapper
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertTrue

class NavigationUseCaseWrapperImplTest {

    @Test
    fun navigate() {
        val x = loadFromResources("validationJsonOutput.json")
        val output = NavigationUseCaseWrapper.init(
            values = "{}",
            processedSurvey = x,
            skipInvalid = false,
            surveyMode = SurveyMode.ONLINE,
            navigationMode = NavigationMode.GROUP_BY_GROUP,
            navigationDirection = NavigationDirection.Start
        ).navigate(getNavigate(engineScript().script))
        val deserialised: NavigationJsonOutput =
            jsonMapper.decodeFromString(NavigationJsonOutput.serializer(), output)
        assertTrue(deserialised.toSave.isNotEmpty())
        assertTrue(deserialised.state["qlarrVariables"]!!.jsonObject.isNotEmpty())
        assertTrue(deserialised.state["qlarrDependents"]!!.jsonObject.isNotEmpty())
        assertTrue(deserialised.toSave.isNotEmpty())
    }
}