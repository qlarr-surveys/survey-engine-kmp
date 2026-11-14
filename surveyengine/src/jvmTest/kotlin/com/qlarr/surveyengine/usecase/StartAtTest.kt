package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.model.NavigationJsonOutput
import com.qlarr.surveyengine.model.exposed.NavigationDirection
import com.qlarr.surveyengine.model.exposed.NavigationIndex
import com.qlarr.surveyengine.model.exposed.NavigationMode
import com.qlarr.surveyengine.model.exposed.SurveyMode
import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.scriptengine.getNavigate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class StartAtTest {

    private val processed = ValidationUseCaseWrapper.create(design().toString()).validate()

    @Test
    fun question_hidden_by_an_earlier_answer_is_forced_relevant() =
        assertOnlyForcedRelevanceDiffers("Q2", forced = setOf("Q2"), alreadyRelevant = setOf("G2"))

    @Test
    fun question_inside_an_irrelevant_group_is_forced_relevant() =
        assertOnlyForcedRelevanceDiffers("Q4", forced = setOf("G3", "Q4"), alreadyRelevant = setOf())

    @Test
    fun answers_hidden_by_an_earlier_answer_are_forced_relevant() =
        assertOnlyForcedRelevanceDiffers("Q3", forced = setOf("Q3A1", "Q3A2"), alreadyRelevant = setOf("G2", "Q3"))

    private fun assertOnlyForcedRelevanceDiffers(questionId: String, forced: Set<String>, alreadyRelevant: Set<String>) {
        val start = relevances(navigate(NavigationDirection.Start, null, "{}", NavigationMode.QUESTION_BY_QUESTION))
        val output = startAt(questionId)
        val startAt = relevances(output)
        assertEquals(NavigationIndex.Question(questionId), output.navigationIndex)
        assertEquals(start.keys, startAt.keys)
        forced.forEach {
            assertEquals(false, start[it], "$it under Start")
            assertEquals(true, startAt[it], "$it under StartAt")
        }
        alreadyRelevant.forEach {
            assertEquals(true, start[it], "$it under Start")
            assertEquals(true, startAt[it], "$it under StartAt")
        }
        assertEquals(start - forced, startAt - forced)
    }

    private fun relevances(output: NavigationJsonOutput): Map<String, Boolean> =
        output.state["qlarrVariables"]!!.jsonObject.mapNotNull { (code, variables) ->
            variables.jsonObject["relevance"]?.let { code to it.jsonPrimitive.boolean }
        }.toMap()

    @Test
    fun survey_is_reduced_to_the_question_and_its_group() {
        val validationJsonOutput = jsonMapper.decodeFromString<ValidationJsonOutput>(processed)
        val reduced = NavigationUseCaseImp(
            validationJsonOutput.toValidationOutput(),
            validationJsonOutput.survey,
            navigationDirection = NavigationDirection.StartAt(NavigationIndex.Question("Q3")),
            navigationMode = NavigationMode.GROUP_BY_GROUP,
            lang = "en",
            skipInvalid = true,
            surveyMode = SurveyMode.ONLINE
        ).navigate(getNavigate()).reducedSurvey
        assertEquals(listOf("G2"), reduced.groups.map { it.code })
        assertEquals(listOf("Q3"), reduced.groups.single().questions.map { it.code })
    }

    @Test
    fun randomized_order_is_applied_like_start() {
        val output = startAt("Q4")
        listOf("G2", "G3").forEach { assertNotNull(variable(output, it, "order"), it) }
        assertEquals(setOf(2, 3), listOf("G2", "G3").map { variable(output, it, "order")!!.jsonPrimitive.int }.toSet())
    }

    @Test
    fun met_quota_does_not_redirect_to_the_end() {
        val output = startAt("Q4", """{"Survey.is_quota_met_QT1":true}""")
        assertEquals(NavigationIndex.Question("Q4"), output.navigationIndex)
        assertEquals(JsonPrimitive(false), variable(output, "Survey", "disqualified"))
    }

    @Test
    fun navigation_mode_is_ignored() {
        NavigationMode.entries.forEach { mode ->
            assertEquals(NavigationIndex.Question("Q2"), startAt("Q2", mode = mode).navigationIndex, mode.name)
        }
    }

    @Test
    fun start_at_round_trips_through_json() {
        val direction = NavigationDirection.StartAt(NavigationIndex.Question("Q80"))
        val encoded = jsonMapper.encodeToString(NavigationDirection.serializer(), direction)
        assertEquals(
            jsonMapper.parseToJsonElement("""{"name":"START_AT","navigationIndex":{"name":"question","questionId":"Q80"}}"""),
            jsonMapper.parseToJsonElement(encoded)
        )
        assertEquals(direction, jsonMapper.decodeFromString(NavigationDirection.serializer(), encoded))
    }

    @Test
    fun start_at_rejects_a_non_question_index() {
        listOf(
            """{"name":"START_AT","navigationIndex":{"name":"group","groupId":"G1"}}""",
            """{"name":"START_AT","navigationIndex":{"name":"groups","groupIds":["G1"]}}""",
            """{"name":"START_AT","navigationIndex":{"name":"end","groupId":"G4"}}""",
            """{"name":"START_AT"}""",
        ).forEach { json ->
            assertFailsWith<SerializationException>(json) {
                jsonMapper.decodeFromString(NavigationDirection.serializer(), json)
            }
        }
    }

    @Test
    fun start_still_lands_on_the_first_relevant_question() {
        val output = navigate(NavigationDirection.Start, null, "{}", NavigationMode.QUESTION_BY_QUESTION)
        assertEquals(NavigationIndex.Question("Q1"), output.navigationIndex)
    }

    private fun startAt(
        questionId: String,
        values: String = "{}",
        mode: NavigationMode = NavigationMode.GROUP_BY_GROUP
    ) = navigate(NavigationDirection.StartAt(NavigationIndex.Question(questionId)), null, values, mode)

    private fun navigate(
        direction: NavigationDirection,
        index: NavigationIndex?,
        values: String,
        mode: NavigationMode
    ): NavigationJsonOutput {
        val output = NavigationUseCaseWrapper.init(
            values = values,
            processedSurvey = processed,
            navigationMode = mode,
            navigationIndex = index,
            navigationDirection = direction,
            skipInvalid = true,
            surveyMode = SurveyMode.ONLINE
        ).navigate(getNavigate())
        return jsonMapper.decodeFromString(NavigationJsonOutput.serializer(), output)
    }

    private fun variable(output: NavigationJsonOutput, code: String, name: String): JsonElement? =
        output.state["qlarrVariables"]!!.jsonObject[code]?.jsonObject?.get(name)

    private fun relevance(text: String) = buildJsonObject {
        put("code", "conditional_relevance")
        put("text", text)
        put("returnType", "boolean")
        put("isActive", true)
    }

    private fun value() = buildJsonObject {
        put("code", "value")
        put("text", "")
        put("returnType", "string")
        put("isActive", false)
    }

    private fun question(code: String, type: String, vararg instructions: JsonObject, answers: List<JsonObject> = listOf()) =
        buildJsonObject {
            put("code", code)
            put("type", type)
            put("instructionList", buildJsonArray {
                add(value())
                instructions.forEach { add(it) }
            })
            if (answers.isNotEmpty()) put("answers", JsonArray(answers))
        }

    private fun answer(code: String, vararg instructions: JsonObject) = buildJsonObject {
        put("code", code)
        put("type", "option")
        put("instructionList", JsonArray(instructions.toList()))
    }

    private fun group(code: String, groupType: String, vararg content: JsonObject) = buildJsonObject {
        put("code", code)
        put("groupType", groupType)
        put("questions", JsonArray(content.filter { it["code"]!!.jsonPrimitive.content.startsWith("Q") }))
        content.firstOrNull { !it["code"]!!.jsonPrimitive.content.startsWith("Q") }?.let {
            put("instructionList", buildJsonArray { add(it) })
        }
    }

    private fun design() = buildJsonObject {
        put("code", "Survey")
        put("defaultLang", buildJsonObject {
            put("code", "en")
            put("name", "English")
        })
        put("navigationMode", "group_by_group")
        put("instructionList", buildJsonArray {
            addJsonObject {
                put("code", "quota_QT1")
                put("text", """Survey.lang == "en"""")
                put("returnType", "boolean")
                put("isActive", true)
            }
            addJsonObject {
                put("code", "random_group")
                put("groups", buildJsonArray {
                    addJsonObject { put("codes", buildJsonArray { add("G2"); add("G3") }) }
                })
            }
        })
        put("groups", buildJsonArray {
            add(group("G1", "GROUP", question("Q1", "text")))
            add(
                group(
                    "G2", "GROUP",
                    question("Q2", "text", relevance("""Q1.value == "x"""")),
                    question(
                        "Q3", "scq",
                        answers = listOf(
                            answer("A1", relevance("""Q1.value == "x"""")),
                            answer("A2", relevance("""Q1.value == "y""""))
                        )
                    )
                )
            )
            add(group("G3", "GROUP", relevance("""Q1.value == "x""""), question("Q4", "text")))
            add(group("G4", "END"))
        })
    }
}
