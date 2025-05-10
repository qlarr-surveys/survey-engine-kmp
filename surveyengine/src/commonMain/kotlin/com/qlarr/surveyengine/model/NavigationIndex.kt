@file:Suppress("SERIALIZER_TYPE_INCOMPATIBLE")

package com.qlarr.surveyengine.model

import kotlinx.serialization.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

fun NavigationIndex.stringIndex() = when (this) {
    is NavigationIndex.End -> "End"
    is NavigationIndex.Group -> this.groupId
    is NavigationIndex.Groups -> this.groupIds.toString()
    is NavigationIndex.Question -> this.questionId
}

@Serializable(with = NavigationIndexSerializer::class)
sealed class NavigationIndex {
    abstract val name: String
    @Transient
    abstract val showError: Boolean
    abstract fun with(showError: Boolean): NavigationIndex


    @Serializable(with = NavigationIndexSerializer::class)
    data class Groups(
        val groupIds: List<String>, 
        @Transient override val showError: Boolean = false,
        override val name: String = "groups"
    ) : NavigationIndex() {
        override fun with(showError: Boolean) = copy(showError = showError)
    }


    @Serializable(with = NavigationIndexSerializer::class)
    data class Group(
        val groupId: String, 
        @Transient override val showError: Boolean = false,
        override val name: String = "group"
    ) : NavigationIndex() {
        override fun with(showError: Boolean) = copy(showError = showError)
    }


    @Serializable(with = NavigationIndexSerializer::class)
    data class Question(
        val questionId: String, 
        @Transient override val showError: Boolean = false,
        override val name: String = "question"
    ) : NavigationIndex() {
        override fun with(showError: Boolean) = copy(showError = showError)
    }


    @Serializable(with = NavigationIndexSerializer::class)
    data class End(
        val groupId: String,
        override val name: String = "end"
    ) : NavigationIndex() {
        @Transient
        override val showError: Boolean = false
        
        override fun with(showError: Boolean): NavigationIndex {
            if (showError) throw IllegalArgumentException("Showing Error at GroupEnd!!!")
            return this
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = NavigationIndex::class)
object NavigationIndexSerializer : KSerializer<NavigationIndex> {
    private val json = Json { ignoreUnknownKeys = true }

    override fun serialize(encoder: Encoder, value: NavigationIndex) {
        val jsonObject = buildJsonObject {
            put("name", JsonPrimitive(value.name))

            when (value) {
                is NavigationIndex.Groups -> {
                    put("groupIds", JsonArray(value.groupIds.map { JsonPrimitive(it) }))
                }
                is NavigationIndex.Group -> {
                    put("groupId", JsonPrimitive(value.groupId))
                }
                is NavigationIndex.Question -> {
                    put("questionId", JsonPrimitive(value.questionId))
                }
                is NavigationIndex.End -> {
                    put("groupId", JsonPrimitive(value.groupId))
                }
            }
            // Note: showError is marked @Transient so we don't serialize it
        }
        (encoder as JsonEncoder).encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): NavigationIndex {
        val jsonElement = when (decoder) {
            is JsonDecoder -> decoder.decodeJsonElement()
            else -> {
                val jsonDecoder = Json.parseToJsonElement(decoder.decodeString())
                if (jsonDecoder is JsonObject) jsonDecoder else throw SerializationException("Expected JsonObject")
            }
        }

        if (jsonElement !is JsonObject) throw SerializationException("Expected JsonObject")

        val name = jsonElement["name"]?.jsonPrimitive?.contentOrNull
            ?: throw SerializationException("Navigation index missing 'name' field")

        return when (name) {
            "groups" -> {
                val groupIds = jsonElement["groupIds"]?.jsonArray?.map { it.jsonPrimitive.content }
                    ?: throw SerializationException("Groups index missing 'groupIds' field")
                NavigationIndex.Groups(groupIds)
            }
            "group" -> {
                val groupId = jsonElement["groupId"]?.jsonPrimitive?.contentOrNull
                    ?: throw SerializationException("Group index missing 'groupId' field")
                NavigationIndex.Group(groupId)
            }
            "question" -> {
                val questionId = jsonElement["questionId"]?.jsonPrimitive?.contentOrNull
                    ?: throw SerializationException("Question index missing 'questionId' field")
                NavigationIndex.Question(questionId)
            }
            "end" -> {
                val groupId = jsonElement["groupId"]?.jsonPrimitive?.contentOrNull
                    ?: throw SerializationException("End index missing 'groupId' field")
                NavigationIndex.End(groupId)
            }
            else -> throw SerializationException("Unknown navigation index: $name")
        }
    }
}

@Serializable(with = NavigationDirectionSerializer::class)
sealed class NavigationDirection {
    abstract val name: String

    @Serializable(with = NavigationDirectionSerializer::class)
    data object Start : NavigationDirection() {
        override val name: String = "START"
    }


    @Serializable(with = NavigationDirectionSerializer::class)
    data object Previous : NavigationDirection() {
        override val name: String = "PREV"
    }


    @Serializable(with = NavigationDirectionSerializer::class)
    data class Jump(
        val navigationIndex: NavigationIndex,
        override val name: String = "JUMP"
    ) : NavigationDirection()


    @Serializable(with = NavigationDirectionSerializer::class)
    object Next : NavigationDirection() {
        override val name: String = "NEXT"
    }



    @Serializable(with = NavigationDirectionSerializer::class)
    object Resume : NavigationDirection() {
        override val name: String = "RESUME"
    }


    @Serializable(with = NavigationDirectionSerializer::class)
    object ChangeLange : NavigationDirection() {
        override val name: String = "CHANGE_LANGE"
    }
}

// Note: The custom serializer/deserializer logic is now handled by kotlinx.serialization
// through the  and @SerialName annotations


enum class NavigationMode {
    ALL_IN_ONE,
    GROUP_BY_GROUP,
    QUESTION_BY_QUESTION;

    companion object {
        fun fromString(string: String?) = when (string?.lowercase()) {
            ALL_IN_ONE.name.lowercase() -> ALL_IN_ONE
            QUESTION_BY_QUESTION.name.lowercase() -> QUESTION_BY_QUESTION
            else -> GROUP_BY_GROUP
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = NavigationDirection::class)
object NavigationDirectionSerializer : KSerializer<NavigationDirection> {
    private val json = Json { ignoreUnknownKeys = true }

    override fun serialize(encoder: Encoder, value: NavigationDirection) {
        // Convert to JsonElement for flexibility
        val jsonObject = buildJsonObject {
            put("name", JsonPrimitive(value.name))

            when (value) {
                is NavigationDirection.Jump -> {
                    put("navigationIndex", json.encodeToJsonElement(value.navigationIndex))
                }
                // Other cases don't need additional fields
                else -> { /* No additional fields needed */ }
            }
        }
        (encoder as JsonEncoder).encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): NavigationDirection {
        // Use JsonDecoder for flexibility
        val jsonElement = when (decoder) {
            is JsonDecoder -> decoder.decodeJsonElement()
            else -> {
                // Fallback for non-JSON decoders
                val jsonDecoder = Json.parseToJsonElement(decoder.decodeString())
                if (jsonDecoder is JsonObject) jsonDecoder else throw SerializationException("Expected JsonObject")
            }
        }

        if (jsonElement !is JsonObject) throw SerializationException("Expected JsonObject")

        val name = jsonElement["name"]?.jsonPrimitive?.contentOrNull
            ?: throw SerializationException("Navigation direction missing 'name' field")

        return when (name) {
            "START" -> NavigationDirection.Start
            "PREV" -> NavigationDirection.Previous
            "NEXT" -> NavigationDirection.Next
            "RESUME" -> NavigationDirection.Resume
            "CHANGE_LANGE" -> NavigationDirection.ChangeLange
            "JUMP" -> {
                val navigationIndex = jsonElement["navigationIndex"]?.let {
                    json.decodeFromJsonElement<NavigationIndex>(it)
                } ?: throw SerializationException("Jump direction missing 'navigationIndex' field")

                NavigationDirection.Jump(navigationIndex)
            }
            else -> throw SerializationException("Unknown navigation direction: $name")
        }
    }
}