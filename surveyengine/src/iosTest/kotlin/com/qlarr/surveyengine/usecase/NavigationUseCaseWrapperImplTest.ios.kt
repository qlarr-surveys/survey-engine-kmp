package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.model.NavigationJsonOutput
import com.qlarr.surveyengine.model.exposed.NavigationDirection
import com.qlarr.surveyengine.model.exposed.NavigationMode
import com.qlarr.surveyengine.model.exposed.SurveyMode
import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.scriptengine.getNavigate
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.jsonObject
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import kotlin.test.Test
import kotlin.test.assertTrue

class NavigationUseCaseWrapperImplTest {

    @Test
    fun navigate() {
        val x = loadFromResources("validationJsonOutput", "json")
        val output = NavigationUseCaseWrapper.init(
            values = "{}",
            processedSurvey = x,
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
        assertTrue(deserialised.toSave.isNotEmpty())
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun loadFromResources(filename: String, extension: String): String {
        val path = NSBundle.mainBundle
            .pathForResource("test-resources/$filename", extension)
            ?: throw IllegalStateException("Could not find $filename in bundle")
        return NSString.stringWithContentsOfFile(
            path = path,
            encoding = NSUTF8StringEncoding,
            error = null
        ) ?: throw IllegalStateException("Could not read $filename")
    }
}