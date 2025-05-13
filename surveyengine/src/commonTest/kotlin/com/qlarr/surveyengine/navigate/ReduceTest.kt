package com.qlarr.surveyengine.navigate

import kotlinx.serialization.json.*
import com.qlarr.surveyengine.model.exposed.NavigationIndex
import com.qlarr.surveyengine.model.ReservedCode
import com.qlarr.surveyengine.model.SurveyLang
import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.context.nestedComponents
import com.qlarr.surveyengine.ext.copyReducedToJSON
import com.qlarr.surveyengine.ext.reduceContent
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.Instruction.SimpleState
import com.qlarr.surveyengine.usecase.wrapToSurvey
import kotlin.test.assertEquals
import kotlin.test.Test

// Extension function to create a copy of a JsonObject
fun JsonObject.copy(): JsonObject {
    return buildJsonObject {
        this@copy.forEach { (key, value) ->
            put(key, value)
        }
    }
}

class ReduceTest {
    @Test
    fun copyReducedToJSON() {
        val reducedSurvey = Survey(
            groups = listOf(
                Group("G1"),
                Group("G2"),
                Group("G3"),
            )
        )

        val orderedSurvey = Survey(
            groups = listOf(
                Group("G5"),
                Group("G1"),
                Group("G3"),
                Group("G2"),
                Group("G4"),
            )
        )

        val surveyObject =
            jsonMapper.parseToJsonElement("{\"code\":\"Survey\",\"groups\":[{\"code\":\"G1\"},{\"code\":\"G2\"},{\"code\":\"G3\"},{\"code\":\"G4\"},{\"code\":\"G5\"}]}").jsonObject


        val newObj = surveyObject.copyReducedToJSON(orderedSurvey, reducedSurvey, defaultLang = SurveyLang.EN.code)
        assertEquals(
            "{\"code\":\"Survey\",\"inCurrentNavigation\":true,\"groups\":[{\"code\":\"G5\",\"inCurrentNavigation\":false},{\"code\":\"G1\",\"inCurrentNavigation\":true},{\"code\":\"G3\",\"inCurrentNavigation\":true},{\"code\":\"G2\",\"inCurrentNavigation\":true},{\"code\":\"G4\",\"inCurrentNavigation\":false}]}",
            newObj.toString()
        )

    }

    @Test
    fun reduceContent() {
        val jsonObject = jsonMapper.parseToJsonElement(
            "{\n" +
                    "  \"content\": {\n" +
                    "    \"en\": {\n" +
                    "      \"survey_title\": \"Customer Satisfaction Survey\",\n" +
                    "      \"title\": \"Customer Satisfaction Survey\"\n" +
                    "    },\n" +
                    "    \"de\": {\n" +
                    "      \"survey_title\": \"Kundenzufriedenheitsumfrage\",\n" +
                    "      \"title\": \"Kundenzufriedenheitsumfrage\"\n" +
                    "    },\n" +
                    "    \"sdf\": \"sdf\"\n" +
                    "  }\n" +
                    "}"
        ).jsonObject
        assertEquals(
            "{\"content\":{\"survey_title\":\"Customer Satisfaction Survey\",\"title\":\"Customer Satisfaction Survey\"}}",
            jsonObject.reduceContent(SurveyLang.EN.code, SurveyLang.EN.code).toString()
        )
        assertEquals(
            "{\"content\":{\"survey_title\":\"Kundenzufriedenheitsumfrage\",\"title\":\"Kundenzufriedenheitsumfrage\"}}",
            jsonObject.reduceContent(SurveyLang.DE.code, SurveyLang.EN.code).toString()
        )

    }
    @Test
    fun reduceContent1() {
        val jsonObject = jsonMapper.parseToJsonElement(
            "{\n" +
                    "  \"content\": {\n" +
                    "    \"en\": {\n" +
                    "      \"survey_title\": \"Customer Satisfaction Survey1\",\n" +
                    "      \"title\": \"Customer Satisfaction Survey2\"\n" +
                    "    },\n" +
                    "    \"de\": {\n" +
                    "      \"survey_title\": \"Kundenzufriedenheitsumfrage\"" +
                    "    },\n" +
                    "    \"sdf\": \"sdf\"\n" +
                    "  }\n" +
                    "}"
        ).jsonObject
        assertEquals(
            "{\"content\":{\"survey_title\":\"Customer Satisfaction Survey1\",\"title\":\"Customer Satisfaction Survey2\"}}",
            jsonObject.reduceContent(SurveyLang.EN.code, SurveyLang.EN.code).toString()
        )
        assertEquals(
            "{\"content\":{\"survey_title\":\"Kundenzufriedenheitsumfrage\",\"title\":\"Customer Satisfaction Survey2\"}}",
            jsonObject.reduceContent(SurveyLang.DE.code, SurveyLang.EN.code).toString()
        )

    }

    @Test
    fun reduceValidationContent() {
        val jsonObject = jsonMapper.parseToJsonElement(
            "{\n" +
                    "  \"code\": \"Afirst_name\",\n" +
                    "  \"validation\": {\n" +
                    "    \"min_length\": {\n" +
                    "      \"content\": {\n" +
                    "        \"de\": \"Welche Art von Vorname hat weniger als 2 Zeichen?\",\n" +
                    "        \"en\": \"What kind of First Name has less than 2 chars?\"\n" +
                    "      },\n" +
                    "      \"input\": 2\n" +
                    "    },\n" +
                    "    \"pattern\": {\n" +
                    "      \"input\": \"^[-a-zA-Z]*\$\",\n" +
                    "      \"template_key\": \"pattern_alpha\"\n" +
                    "    }\n" +
                    "  }\n" +
                    "}"
        ).jsonObject
        assertEquals(
            "{\"code\":\"Afirst_name\",\"validation\":{\"min_length\":{\"content\":\"What kind of First Name has less than 2 chars?\",\"input\":2},\"pattern\":{\"input\":\"^[-a-zA-Z]*\$\",\"template_key\":\"pattern_alpha\"}}}",
            jsonObject.reduceContent(SurveyLang.EN.code, SurveyLang.EN.code).toString()
        )
        assertEquals(
            "{\"code\":\"Afirst_name\",\"validation\":{\"min_length\":{\"content\":\"Welche Art von Vorname hat weniger als 2 Zeichen?\",\"input\":2},\"pattern\":{\"input\":\"^[-a-zA-Z]*\$\",\"template_key\":\"pattern_alpha\"}}}",
            jsonObject.reduceContent(SurveyLang.DE.code, SurveyLang.EN.code).toString()
        )

    }


    @Test
    fun reduce1() {
        val survey = Group(
            code = "G1",
            questions = listOf(
                Question(
                    code = "Q1",
                    instructionList = listOf(
                        Instruction.Reference("reference_1", listOf("Q1A1.label"), SurveyLang.EN.code),
                        SimpleState("(Q1A1.value=='S3') ? 'S1' : 'S3'", ReservedCode.Value, isActive = true)
                    ),
                    answers = listOf(
                        Answer(
                            code = "A1",
                            instructionList = listOf(SimpleState("S3", ReservedCode.Value))
                        )
                    )
                ), Question(
                    code = "Q2",
                    instructionList = listOf(
                        Instruction.Reference("reference_1", listOf("Q1A2.label"), SurveyLang.EN.code),
                        SimpleState("(Q2A1.value=='S3') ? 'S1' : 'S3'", ReservedCode.Value, isActive = true)
                    ),
                    answers = listOf(
                        Answer(
                            code = "A1",
                            instructionList = listOf(SimpleState("S3", ReservedCode.Value))
                        )
                    )
                )
            )
        ).wrapToSurvey()
        assertEquals(
            (
                    Group(
                        code = "G1",
                        questions = listOf(
                            Question(
                                code = "Q2",
                                instructionList = listOf(
                                    Instruction.Reference("reference_1", listOf("Q1A2.label"), SurveyLang.EN.code),
                                    SimpleState("(Q2A1.value=='S3') ? 'S1' : 'S3'", ReservedCode.Value, isActive = true)
                                ),
                                answers = listOf(
                                    Answer(
                                        code = "A1",
                                        instructionList = listOf(SimpleState("S3", ReservedCode.Value))
                                    )
                                )
                            )
                        )
                    ).wrapToSurvey()), survey.reduce(NavigationIndex.Question("Q2"))
        )
    }


    @Test
    fun reduce2() {
        val component = Group(
            code = "G1",
            questions = listOf(
                Question(
                    code = "Q1",
                    instructionList = listOf(
                        Instruction.Reference("reference_1", listOf("Q1A1.label"), SurveyLang.EN.code),
                        SimpleState("(Q1A1.value=='S3') ? 'S1' : 'S3'", ReservedCode.Value, isActive = true)
                    ),
                    answers = listOf(
                        Answer(
                            code = "A1",
                            instructionList = listOf(SimpleState("S3", ReservedCode.Value))
                        )
                    )
                ), Question(
                    code = "Q2",
                    instructionList = listOf(
                        Instruction.Reference("reference_1", listOf("Q1A2.label"), SurveyLang.EN.code),
                        SimpleState("(Q2A1.value=='S3') ? 'S1' : 'S3'", ReservedCode.Value, isActive = true)
                    ),
                    answers = listOf(
                        Answer(
                            code = "A1",
                            instructionList = listOf(SimpleState("S3", ReservedCode.Value))
                        )
                    )
                )
            )
        ).wrapToSurvey()
        assertEquals(
            (
                    Group(
                        code = "G1",
                        questions = listOf(
                            Question(
                                code = "Q1",
                                instructionList = listOf(
                                    Instruction.Reference("reference_1", listOf("Q1A1.label"), SurveyLang.EN.code),
                                    SimpleState("(Q1A1.value=='S3') ? 'S1' : 'S3'", ReservedCode.Value, isActive = true)
                                ),
                                answers = listOf(
                                    Answer(
                                        code = "A1",
                                        instructionList = listOf(SimpleState("S3", ReservedCode.Value))
                                    )
                                )
                            ), Question(
                                code = "Q2",
                                instructionList = listOf(
                                    Instruction.Reference("reference_1", listOf("Q1A2.label"), SurveyLang.EN.code),
                                    SimpleState("(Q2A1.value=='S3') ? 'S1' : 'S3'", ReservedCode.Value, isActive = true)
                                ),
                                answers = listOf(
                                    Answer(
                                        code = "A1",
                                        instructionList = listOf(SimpleState("S3", ReservedCode.Value))
                                    )
                                )
                            )
                        )
                    ).wrapToSurvey()), component.reduce(NavigationIndex.Group("G1"))
        )
    }

    @Test
    fun reduce3() {
        val survey = Survey(
            groups = listOf(
                Group(
                    "G1",
                    questions = listOf(
                        Question(
                            "Q1",
                            answers = listOf(
                                Answer("A1")
                            )
                        ), Question(
                            "Q2",
                            answers = listOf(
                                Answer("A1")
                            )
                        )
                    )
                ),
                Group(
                    "G2",
                    questions = listOf(
                        Question(
                            "Q3",
                            answers = listOf(
                                Answer("A1")
                            )
                        ), Question(
                            "Q4",
                            answers = listOf(
                                Answer("A1")
                            )
                        )
                    )
                )
            )
        )
        assertEquals(
            listOf(
                ChildlessComponent("Survey", "", SurveyElementType.SURVEY),
                ChildlessComponent("G1", "Survey", SurveyElementType.GROUP),
                ChildlessComponent("Q2", "G1", SurveyElementType.QUESTION),
                ChildlessComponent("Q2A1", "Q2", SurveyElementType.ANSWER)
            ),
            survey.reduce(NavigationIndex.Question("Q2")).nestedComponents()
        )
        assertEquals(
            listOf(
                ChildlessComponent("Survey", "", SurveyElementType.SURVEY),
                ChildlessComponent("G1", "Survey", SurveyElementType.GROUP),
                ChildlessComponent("Q1", "G1", SurveyElementType.QUESTION),
                ChildlessComponent("Q1A1", "Q1", SurveyElementType.ANSWER),
                ChildlessComponent("Q2", "G1", SurveyElementType.QUESTION),
                ChildlessComponent("Q2A1", "Q2", SurveyElementType.ANSWER)
            ), survey.reduce(NavigationIndex.Group("G1")).nestedComponents()
        )
        assertEquals(
            listOf(
                ChildlessComponent("Survey", "", SurveyElementType.SURVEY),
                ChildlessComponent("G2", "Survey", SurveyElementType.GROUP),
                ChildlessComponent("Q3", "G2", SurveyElementType.QUESTION),
                ChildlessComponent("Q3A1", "Q3", SurveyElementType.ANSWER)
            ),
            survey.reduce(NavigationIndex.Question("Q3")).nestedComponents()
        )
        assertEquals(
            listOf(
                ChildlessComponent("Survey", "", SurveyElementType.SURVEY),
                ChildlessComponent("G2", "Survey", SurveyElementType.GROUP),
                ChildlessComponent("Q4", "G2", SurveyElementType.QUESTION),
                ChildlessComponent("Q4A1", "Q4", SurveyElementType.ANSWER)
            ),
            survey.reduce(NavigationIndex.Question("Q4")).nestedComponents()
        )
        assertEquals(
            listOf(
                ChildlessComponent("Survey", "", SurveyElementType.SURVEY),
                ChildlessComponent("G2", "Survey", SurveyElementType.GROUP),
                ChildlessComponent("Q3", "G2", SurveyElementType.QUESTION),
                ChildlessComponent("Q3A1", "Q3", SurveyElementType.ANSWER),
                ChildlessComponent("Q4", "G2", SurveyElementType.QUESTION),
                ChildlessComponent("Q4A1", "Q4", SurveyElementType.ANSWER)
            ), survey.reduce(NavigationIndex.Group("G2")).nestedComponents()
        )
    }

}