package com.qlarr.surveyengine.model

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*


@Serializable
data class NavigationUseCaseInput(
    val values: Map<String, @Contextual Any> = mapOf(),
    val lang: String? = null,
    val navigationMode: NavigationMode? = null,
    val navigationInfo: NavigationInfo = NavigationInfo()
)

object AnySerializer : KSerializer<Any> {

    override fun serialize(encoder: Encoder, value: Any) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("This serializer can only be used with JSON")

        when (value) {
            is JsonElement -> jsonEncoder.encodeJsonElement(value)
            is String -> jsonEncoder.encodeJsonElement(JsonPrimitive(value))
            is Number -> jsonEncoder.encodeJsonElement(JsonPrimitive(value))
            is Boolean -> {
                jsonEncoder.encodeJsonElement(JsonPrimitive(value))
            }
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val map = value as Map<String, Any>
                jsonEncoder.encodeJsonElement(JsonObject(map.mapValues { (_, v) ->
                    when (v) {
                        is JsonElement -> v
                        is String -> JsonPrimitive(v)
                        is Number -> JsonPrimitive(v)
                        is Boolean -> JsonPrimitive(v)
                        else -> throw SerializationException("Unsupported type for nested map value: ${v::class}")
                    }
                }))
            }
            is List<*> -> {
                val list = value as List<Any?>
                jsonEncoder.encodeJsonElement(JsonArray(list.map { item ->
                    when (item) {
                        is JsonElement -> item
                        is String -> JsonPrimitive(item)
                        is Number -> JsonPrimitive(item)
                        is Boolean -> JsonPrimitive(item)
                        null -> JsonNull
                        else -> throw SerializationException("Unsupported type for list item: ${item::class}")
                    }
                }))
            }
            else -> throw SerializationException("Unsupported type: ${value::class}")
        }
    }

    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("Any", SerialKind.CONTEXTUAL)


    override fun deserialize(decoder: Decoder): Any {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("This serializer can only be used with JSON")

        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.boolean
                element.longOrNull != null -> element.long
                element.doubleOrNull != null -> element.double
                else -> element.content // Fallback to string
            }
            is JsonObject -> element.mapValues { (_, v) -> deserializeJsonElement(v) }
            is JsonArray -> element.map { deserializeJsonElement(it) }
            JsonNull -> null as Any
        }
    }

    private fun deserializeJsonElement(element: JsonElement): Any? {
        return when (element) {
            is JsonPrimitive -> when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.boolean
                element.longOrNull != null -> element.long
                element.doubleOrNull != null -> element.double
                else -> element.content
            }
            is JsonObject -> element.mapValues { (_, v) -> deserializeJsonElement(v) }
            is JsonArray -> element.map { deserializeJsonElement(it) }
            JsonNull -> null
        }
    }
}