package com.qlarr.surveyengine.usecase

import com.qlarr.scriptengine.getNavigate
import com.qlarr.surveyengine.common.loadFromResources
import com.qlarr.surveyengine.ext.engineScript
import com.qlarr.surveyengine.model.exposed.NavigationDirection
import com.qlarr.surveyengine.model.exposed.NavigationMode
import com.qlarr.surveyengine.model.exposed.SurveyMode
import kotlin.test.Test

class NavigationUseCaseWrapperImplTest {

    @Test
    fun navigate() {
        val x = loadFromResources("3.json")
        val output = NavigationUseCaseWrapper.init(
            scriptEngine = getNavigate(engineScript().script),
            values = "{}",
            processedSurvey = x,
            skipInvalid = false,
            surveyMode = SurveyMode.ONLINE,
            navigationMode = NavigationMode.GROUP_BY_GROUP,
            navigationDirection = NavigationDirection.Start
        ).navigate()
        println(output)
    }
}