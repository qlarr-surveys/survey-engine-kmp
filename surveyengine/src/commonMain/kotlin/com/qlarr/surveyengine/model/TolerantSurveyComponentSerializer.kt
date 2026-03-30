@file:Suppress("SERIALIZER_TYPE_INCOMPATIBLE")

package com.qlarr.surveyengine.model

import com.qlarr.surveyengine.ext.isAnswerCode
import com.qlarr.surveyengine.ext.isGroupCode
import com.qlarr.surveyengine.ext.isQuestionCode
import com.qlarr.surveyengine.ext.isSurveyCode
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule

/**
 * A tolerant serializer that collects deserialization issues instead of failing immediately.
 * Issues are accumulated during deserialization and returned with the result.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = SurveyComponent::class)
object TolerantSurveyComponentSerializer : KSerializer<SurveyComponent> {
    private val json = Json { ignoreUnknownKeys = true }

    // Storage for collecting issues during deserialization
    private var currentIssues: MutableList<DeserializationIssue>? = null

    private fun addIssue(issue: DeserializationIssue) {
        currentIssues?.add(issue)
    }

    override fun serialize(encoder: Encoder, value: SurveyComponent) {
        // Reuse the original serializer's logic
        SurveyComponentSerializer.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): SurveyComponent {
        val jsonElement = when (decoder) {
            is JsonDecoder -> decoder.decodeJsonElement()
            else -> {
                val jsonDecoder = Json.parseToJsonElement(decoder.decodeString())
                if (jsonDecoder is JsonObject) jsonDecoder else throw SerializationException("Expected JsonObject")
            }
        }

        if (jsonElement !is JsonObject) throw SerializationException("Expected JsonObject")

        val code: String =
            jsonElement["code"]?.jsonPrimitive?.contentOrNull ?: throw SerializationException("Code Missing")

        val instructionList: List<Instruction> = deserializeListTolerant(
            jsonElement["instructionList"],
            serializer<Instruction>(),
            "$code.instructionList",
            addInstructionFragment = true
        )

        val errors: List<ComponentError> = deserializeListTolerant(
            jsonElement["errors"],
            serializer<ComponentError>(),
            "$code.errors"
        )

        return if (code.isSurveyCode()) {
            val groups: List<Group> = deserializeListTolerant(
                jsonElement["groups"],
                TolerantSurveyComponentSerializer,
                "Survey.groups"
            ).filterIsInstance<Group>()
            Survey(instructionList, groups, errors)
        } else if (code.isGroupCode()) {
            val groupType: GroupType = jsonElement["groupType"]?.jsonPrimitive?.let {
                try {
                    json.decodeFromJsonElement(serializer<GroupType>(), it)
                } catch (e: Exception) {
                    addIssue(
                        DeserializationIssue(
                            path = "$code.groupType",
                            message = e.message ?: "Failed to deserialize groupType"
                        )
                    )
                    GroupType.GROUP
                }
            } ?: GroupType.GROUP

            val questions: List<Question> = deserializeListTolerant(
                jsonElement["questions"],
                TolerantSurveyComponentSerializer,
                "$code.questions"
            ).filterIsInstance<Question>()
            Group(code, instructionList, questions, groupType, errors)
        } else if (code.isQuestionCode()) {
            val answers: List<Answer> = deserializeListTolerant(
                jsonElement["answers"],
                TolerantSurveyComponentSerializer,
                "$code.answers"
            ).filterIsInstance<Answer>()
            Question(code, instructionList, answers, errors)
        } else if (code.isAnswerCode()) {
            val answers: List<Answer> = deserializeListTolerant(
                jsonElement["answers"],
                TolerantSurveyComponentSerializer,
                "$code.answers"
            ).filterIsInstance<Answer>()
            Answer(code, instructionList, answers, errors)
        } else {
            throw SerializationException("Invalid component code")
        }
    }

    /**
     * Deserializes a list with tolerance - skips elements that fail to deserialize
     * and records issues for each failure.
     */
    private fun <T> deserializeListTolerant(
        jsonElement: JsonElement?,
        serializer: KSerializer<T>,
        path: String,
        addInstructionFragment:Boolean = false
    ): List<T> {
        if (jsonElement == null) return emptyList()

        if (jsonElement !is JsonArray) {
            addIssue(
                DeserializationIssue(
                    path = path,
                    message = "Expected array but got ${jsonElement::class.simpleName}",
                    instructionJsonFragment = if (addInstructionFragment) jsonElement.toString() else ""
                )
            )
            return emptyList()
        }

        val results = mutableListOf<T>()
        jsonElement.forEachIndexed { index, element ->
            try {
                val item = json.decodeFromJsonElement(serializer, element)
                results.add(item)
            } catch (e: Exception) {
                addIssue(
                    DeserializationIssue(
                        path = "$path[$index]",
                        message = e.message ?: "Failed to deserialize element",
                        instructionJsonFragment = if (addInstructionFragment) element.toString() else ""

                    )
                )
            }
        }
        return results
    }

    /**
     * Deserializes a Survey from JSON string with tolerance, returning both the survey
     * and any deserialization issues encountered.
     */
    fun deserializeTolerant(jsonString: String): DeserializationResult {
        val issues = mutableListOf<DeserializationIssue>()
        currentIssues = issues

        return try {
            val jsonInstance = Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }

            val survey = jsonInstance.decodeFromString(TolerantSurveyComponentSerializer, jsonString) as Survey
            DeserializationResult(survey, issues)
        } finally {
            currentIssues = null
        }
    }
}
