package com.qlarr.surveyengine.context.execute

import com.qlarr.surveyengine.model.Group
import com.qlarr.surveyengine.model.Question
import com.qlarr.surveyengine.model.RepeatInfo
import com.qlarr.surveyengine.model.Survey
import kotlin.test.Test
import kotlin.test.assertEquals

class ExecuteUtilsTest {

    @Test
    fun sanitize_drops_repeatable_templates_but_keeps_repeated_and_normal() {
        val survey = Survey(
            groups = listOf(
                Group(
                    "G1",
                    repeatInfo = RepeatInfo.Repeatable(range = listOf("a"), relevanceInstruction = "Qx.includes('{{repeat_token}}')"),
                    questions = listOf(Question("Q1"))
                ),
                Group("G2", repeatInfo = RepeatInfo.Repeated("a"), questions = listOf(Question("Q2"))),
                Group("G3", questions = listOf(Question("Q3")))
            )
        )

        val sanitized = survey.sanitize()

        assertEquals(listOf("G2", "G3"), sanitized.groups.map { it.code })
    }
}
