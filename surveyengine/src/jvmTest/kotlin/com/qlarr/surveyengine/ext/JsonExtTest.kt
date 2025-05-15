package com.qlarr.surveyengine.ext

import com.qlarr.surveyengine.common.loadFromResources
import com.qlarr.surveyengine.model.jsonMapper
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonExtTest {


    @Test
    fun validate() {
        val surveyString = loadFromResources("2.json")
        val surveyObject = jsonMapper.parseToJsonElement(surveyString).jsonObject
        val flatJsonString = JsonExt.flatObject(surveyString)
        val flatJsonObject = jsonMapper.parseToJsonElement(flatJsonString).jsonObject
        val rebuiltJsonString = JsonExt.addChildren(flatJsonObject["Survey"].toString(),"Survey",flatJsonString)
        val rebuiltJsonObject = jsonMapper.parseToJsonElement(rebuiltJsonString)
        assertEquals(surveyObject, rebuiltJsonObject)

    }
}