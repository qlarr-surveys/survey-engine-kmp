package com.qlarr.surveyengine.model.adapters

import com.qlarr.surveyengine.model.exposed.ReturnType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = ReturnType::class)
object ReturnTypeSerializer : KSerializer<ReturnType> {
    override fun serialize(encoder: Encoder, value: ReturnType) {
        val jsonElement = when (value) {
            is ReturnType.Boolean -> {
                JsonPrimitive("boolean")
            }

            is ReturnType.String -> {
                JsonPrimitive("string")
            }

            is ReturnType.Int -> {
                JsonPrimitive("int")
            }

            is ReturnType.Double -> {
                JsonPrimitive("double")
            }

            is ReturnType.List -> {
                JsonPrimitive("list")
            }

            is ReturnType.Map -> {
                JsonPrimitive("map")
            }

            is ReturnType.Date -> {
                JsonPrimitive("date")
            }

            is ReturnType.File -> {
                JsonPrimitive("file")
            }

            is ReturnType.Enum -> {
                buildJsonObject {
                    put("type", "enum")
                    put("values", JsonArray(value.values.map { JsonPrimitive(it) }))
                }
            }
        }
        encoder.encodeSerializableValue(JsonElement.serializer(), jsonElement)
    }


    override fun deserialize(decoder: Decoder): ReturnType {
        val jsonElement = when (decoder) {
            is JsonDecoder -> decoder.decodeJsonElement()
            else -> {
                // Fallback for non-JSON decoders
                val jsonDecoder = Json.parseToJsonElement(decoder.decodeString())
                if (jsonDecoder is JsonObject) jsonDecoder else throw SerializationException("Expected JsonObject")
            }
        }
        return when (jsonElement) {
            is JsonPrimitive -> {
                when (jsonElement.content.lowercase()) {
                    "boolean" -> ReturnType.Boolean
                    "string" -> ReturnType.String
                    "int" -> ReturnType.Int
                    "double" -> ReturnType.Double
                    "list" -> ReturnType.List
                    "map" -> ReturnType.Map
                    "date" -> ReturnType.Date
                    "file" -> ReturnType.File
                    else -> throw SerializationException("Unknown return type: ${jsonElement.content}")
                }
            }

            is JsonObject -> {
                val type = jsonElement["type"]?.jsonPrimitive?.content
                when (type) {
                    "enum" -> {
                        val values = jsonElement["values"]?.jsonArray?.map { it.jsonPrimitive.content }
                            ?: throw SerializationException("Missing 'values' field for enum type")
                        ReturnType.Enum(values.toSet())
                    }

                    else -> throw SerializationException("Unknown object type: $type")
                }
            }

            else -> throw SerializationException("Expected JsonPrimitive or JsonObject, got ${jsonElement::class.simpleName}")
        }
    }
}