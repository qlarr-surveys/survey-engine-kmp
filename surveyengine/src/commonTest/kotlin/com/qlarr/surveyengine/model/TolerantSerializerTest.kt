package com.qlarr.surveyengine.model

import com.qlarr.surveyengine.ext.copyComponentsToJson
import com.qlarr.surveyengine.ext.copyDeserializationIssues
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TolerantSerializerTest {

    @Test
    fun testValidSurveyDeserialization() {
        val validJson = """
        {
            "code": "Survey",
            "groups": [
                {
                    "code": "G1",
                    "questions": [
                        {
                            "code": "Q1",
                            "answers": []
                        }
                    ]
                }
            ]
        }
        """.trimIndent()

        val result = TolerantSurveyComponentSerializer.deserializeTolerant(validJson)

        assertEquals("Survey", result.survey.code)
        assertEquals(1, result.survey.groups.size)
        assertEquals("G1", result.survey.groups[0].code)
        assertEquals(0, result.issues.size)
    }

    @Test
    fun testSurveyWithMultipleIssues() {
        val jsonWithMultipleIssues = """
        {
            "code": "Survey",
            "groups": [
                {
                    "code": "G1",
                    "questions": [
                        {
                            "code": "Q1",
                            "answers": []
                        },
                        {
                            "code": "BadQuestion",
                            "answers": []
                        }
                    ]
                },
                {
                    "code": "BadGroup",
                    "questions": []
                },
                {
                    "code": "G2",
                    "questions": []
                }
            ]
        }
        """.trimIndent()

        val result = TolerantSurveyComponentSerializer.deserializeTolerant(jsonWithMultipleIssues)

        assertEquals("Survey", result.survey.code)
        assertEquals(2, result.survey.groups.size)
        assertEquals(1, result.survey.groups[0].questions.size)

        // Should have recorded multiple issues
        assertTrue(result.issues.size >= 2)
    }

    @Test
    fun testCopyDeserializationIssuesToJson() {
        val jsonWithInvalidQuestion = """
        {
            "code": "Survey",
            "groups": [
                {
                    "code": "G1",
                    "questions": [
                        {
                            "code": "Q1",
                            "answers": []
                        },
                        {
                            "code": "InvalidCode123",
                            "answers": []
                        },
                        {
                            "code": "Q2",
                            "answers": []
                        }
                    ]
                }
            ]
        }
        """.trimIndent()

        val result = TolerantSurveyComponentSerializer.deserializeTolerant(jsonWithInvalidQuestion)
        val originalJson = jsonMapper.parseToJsonElement(jsonWithInvalidQuestion).jsonObject

        // Copy deserialization issues into the JSON
        val jsonWithIssues = originalJson.copyDeserializationIssues(result.issues)

        // Navigate to the failed question node (index 1)
        val groups = jsonWithIssues["groups"]?.jsonArray
        assertNotNull(groups)
        val group = groups[0].jsonObject
        val questions = group["questions"]?.jsonArray
        assertNotNull(questions)

        // The failed question should have deserializationIssues attached to it
        val failedQuestion = questions[1].jsonObject
        assertEquals("InvalidCode123", failedQuestion["code"]?.jsonPrimitive?.content)

        val questionIssues = failedQuestion["deserializationIssues"]?.jsonArray!!
        assertTrue(questionIssues.isNotEmpty(), "Issue should have a message")
    }

    @Test
    fun testCopyDeserializationIssuesToGroupNode() {
        val jsonWithInvalidGroup = """
        {
            "code": "Survey",
            "groups": [
                {
                    "code": "G1",
                    "questions": []
                },
                {
                    "code": "InvalidGroupCode",
                    "questions": []
                },
                {
                    "code": "G2",
                    "questions": []
                }
            ]
        }
        """.trimIndent()

        val result = TolerantSurveyComponentSerializer.deserializeTolerant(jsonWithInvalidGroup)
        val originalJson = jsonMapper.parseToJsonElement(jsonWithInvalidGroup).jsonObject

        // Copy deserialization issues into the JSON
        val jsonWithIssues = originalJson.copyDeserializationIssues(result.issues)

        // Navigate to the failed group node (index 1)
        val groups = jsonWithIssues["groups"]?.jsonArray!!

        val failedGroup = groups[1].jsonObject
        assertEquals("InvalidGroupCode", failedGroup["code"]?.jsonPrimitive?.content)

        val groupIssues = failedGroup["deserializationIssues"]?.jsonArray!!
        assertTrue(groupIssues.isNotEmpty(), "Issue should have a message")
    }

    @Test
    fun testInvalidGroupWithNestedContent() {
        val jsonWithInvalidGroupWithContent = """
        {
            "code": "Survey",
            "groups": [
                {
                    "code": "G1",
                    "questions": [
                        {
                            "code": "Q1",
                            "answers": [
                                {"code": "A1"},
                                {"code": "A2"}
                            ]
                        }
                    ]
                },
                {
                    "code": "BadGroupCode",
                    "questions": [
                        {
                            "code": "Q2",
                            "answers": [
                                {"code": "A3"},
                                {"code": "A4"}
                            ]
                        },
                        {
                            "code": "Q3",
                            "answers": [
                                {"code": "A5"}
                            ]
                        }
                    ]
                },
                {
                    "code": "G2",
                    "questions": []
                }
            ]
        }
        """.trimIndent()

        val result = TolerantSurveyComponentSerializer.deserializeTolerant(jsonWithInvalidGroupWithContent)

        // Should skip the invalid group but keep the valid ones
        assertEquals("Survey", result.survey.code)
        assertEquals(2, result.survey.groups.size)
        assertEquals("G1", result.survey.groups[0].code)
        assertEquals("G2", result.survey.groups[1].code)


        val originalJson = jsonMapper.parseToJsonElement(jsonWithInvalidGroupWithContent).jsonObject
        val jsonWithIssues = originalJson.copyDeserializationIssues(result.issues)

        // Navigate to the failed group node (index 1)
        val groups = jsonWithIssues["groups"]?.jsonArray
        assertNotNull(groups)

        // The failed group should still be in the JSON with its nested content intact
        val failedGroup = groups[1].jsonObject
        assertEquals("BadGroupCode", failedGroup["code"]?.jsonPrimitive?.content)

        // Verify the nested questions and answers are still there
        val questions = failedGroup["questions"]?.jsonArray!!
        val firstQuestion = questions[0].jsonObject
        assertEquals("Q2", firstQuestion["code"]?.jsonPrimitive?.content)
        val answers = firstQuestion["answers"]?.jsonArray
        assertEquals(2, answers!!.size, "Question should have its answers")

        // Verify the deserialization issue is attached to the failed group
        val groupIssues = failedGroup["deserializationIssues"]?.jsonArray
        assertTrue(groupIssues!!.isNotEmpty())

    }

     @Test
    fun testInvalidInstructionInjectedBack() {
         // Use malformed JSON that will definitely fail parsing
         val jsonWithInvalidInstruction = """
        {
            "code": "Survey",
            "groups": [
                {
                    "code": "G1",
                    "instructionList": [
                        {
                            "code": "value"
                        },
                        {
                            "code": "invalid"
                        }
                    ],
                    "questions": []
                }
            ]
        }
        """.trimIndent()

         val result = TolerantSurveyComponentSerializer.deserializeTolerant(jsonWithInvalidInstruction)

         // Now copy issues back
         val originalJson = jsonMapper.parseToJsonElement(jsonWithInvalidInstruction).jsonObject
         val surveyWithComponents = result.survey.copyComponentsToJson(originalJson)
         val surveyWithIssues = surveyWithComponents.copyDeserializationIssues(result.issues)

         val instructionWithError = (((surveyWithIssues["groups"] as JsonArray)[0] as JsonObject)["instructionList"] as JsonArray)[1]
         assertEquals("{\"code\":\"invalid\",\"deserializationIssues\":[\"Invalid JSON for instruction\"]}", instructionWithError.toString())

         // Verify issues were copied
     }

}
