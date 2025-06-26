package com.qlarr.surveyengine.ext

import com.qlarr.surveyengine.scriptengine.commonScript
import com.qlarr.surveyengine.scriptengine.engineScript
import kotlin.test.Test
import kotlin.test.assertTrue


class ScriptUtilsTest {

    @Test
    fun testIndicesAreResolvedWithRandomGroups() {
        assertTrue(engineScript().script.isNotEmpty())
        assertTrue(commonScript().script.isNotEmpty())
    }

}