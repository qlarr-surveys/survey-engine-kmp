package com.qlarr.surveyengine.context.assemble

import com.qlarr.surveyengine.model.ChildlessComponent
import com.qlarr.surveyengine.model.SurveyElementType
import kotlin.test.assertEquals
import kotlin.test.Test


class BuildUtilsKtTest {


    @Test
    fun  resolves_common_parent_and_parent() {
        val childlessComponentList = listOf(
            ChildlessComponent("Survey", "", SurveyElementType.SURVEY),
            ChildlessComponent("G1", "Survey", SurveyElementType.GROUP),
            ChildlessComponent("Q1", "G1", SurveyElementType.QUESTION),
            ChildlessComponent("Q2", "G1", SurveyElementType.QUESTION),
            ChildlessComponent("Q1A1", "Q1", SurveyElementType.ANSWER),
            ChildlessComponent("Q1A2", "Q1", SurveyElementType.ANSWER),
        )

        assertEquals(listOf("G1", "Survey"), childlessComponentList.parents("Q2"))
        assertEquals(listOf(), childlessComponentList.parents("Survey"))
        assertEquals(listOf("Q1", "G1", "Survey"), childlessComponentList.parents("Q1A2"))
        assertEquals("Q1", childlessComponentList.commonParent("Q1A1", "Q1A2")!!.code)
        assertEquals("Survey", childlessComponentList.commonParent("G1", "Q1")!!.code)
        assertEquals("G1", childlessComponentList.commonParent("Q1A1", "Q1")!!.code)
        assertEquals("G1", childlessComponentList.commonParent("Q1A1", "Q2")!!.code)
    }
}