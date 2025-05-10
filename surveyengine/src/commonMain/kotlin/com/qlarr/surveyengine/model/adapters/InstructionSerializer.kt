package com.qlarr.surveyengine.model.adapters

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import com.qlarr.surveyengine.ext.VALID_REFERENCE_INSTRUCTION_PATTERN
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.Instruction.*
import kotlinx.serialization.builtins.ListSerializer

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = Instruction::class)
object InstructionSerializer : KSerializer<Instruction> {
    private val json = Json { ignoreUnknownKeys = true }

    override fun serialize(encoder: Encoder, value: Instruction) {
        // Convert to JsonElement first for more flexibility
        val jsonObject = buildJsonObject {
            put("code", value.code)

            when (value) {
                is Reference -> {
                    put("references", JsonArray(value.references.map { JsonPrimitive(it) }))
                    put("lang", value.lang)
                }

                is RandomGroups -> {
                    put("groups", json.encodeToJsonElement(ListSerializer(serializer<RandomGroup>()), value.groups))
                }

                is PriorityGroups -> {
                    put("priorities", json.encodeToJsonElement(ListSerializer(serializer<PriorityGroup>()), value.priorities))
                }

                is ParentRelevance -> {
                    put("children", JsonArray(value.children.map { childList ->
                        JsonArray(childList.map { JsonPrimitive(it) })
                    }))
                }

                is State -> {
                    put("text", value.text)
                    put("returnType", value.returnType.name.lowercase())
                    put("isActive", value.isActive)

                    if (value is SkipInstruction) {
                        put("skipToComponent", value.skipToComponent)
                        put("condition", value.condition)
                        put("toEnd", value.toEnd)
                    }
                }
            }

            if (value.errors.isNotEmpty()) {
                put("errors", json.encodeToJsonElement(ListSerializer(serializer<InstructionError>()), value.errors))
            }
        }
        (encoder as JsonEncoder).encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): Instruction {
        // Use JsonDecoder for flexibility
        val jsonElement = when (decoder) {
            is JsonDecoder -> decoder.decodeJsonElement()
            else -> {
                // Fallback for non-JSON decoders (not ideal, but better than nothing)
                val jsonDecoder = Json.parseToJsonElement(decoder.decodeString())
                if (jsonDecoder is JsonObject) jsonDecoder else throw SerializationException("Expected JsonObject")
            }
        }

        if (jsonElement !is JsonObject) throw SerializationException("Expected JsonObject")

        val code = jsonElement["code"]?.jsonPrimitive?.contentOrNull ?: ""
        val errors = jsonElement["errors"]?.let {
            json.decodeFromJsonElement(ListSerializer(serializer<InstructionError>()), it)
        } ?: listOf()

        // Extract fields directly from JSON
        val text = jsonElement["text"]?.jsonPrimitive?.contentOrNull
        val lang = jsonElement["lang"]?.jsonPrimitive?.contentOrNull
        val condition = jsonElement["condition"]?.jsonPrimitive?.contentOrNull
        val isActive = jsonElement["isActive"]?.jsonPrimitive?.booleanOrNull
        val skipToComponent = jsonElement["skipToComponent"]?.jsonPrimitive?.contentOrNull ?: ""
        val toEnd = jsonElement["toEnd"]?.jsonPrimitive?.booleanOrNull ?: false

        val returnType = jsonElement["returnType"]?.let { returnTypeElement ->
            when {
                returnTypeElement.jsonPrimitive.isString -> {
                    ReturnType.fromString(returnTypeElement.jsonPrimitive.content)
                }
                returnTypeElement is JsonObject -> {
                    val name = returnTypeElement["name"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
                    ReturnType.fromString(name)
                }
                else -> null
            }
        }

        // Extract specialized fields
        val references = jsonElement["references"]?.jsonArray?.map { it.jsonPrimitive.content } ?: listOf()

        val groups = jsonElement["groups"]?.let {
            json.decodeFromJsonElement(ListSerializer(serializer<RandomGroup>()), it)
        } ?: listOf()

        val priorities = jsonElement["priorities"]?.let {
            json.decodeFromJsonElement(ListSerializer(serializer<PriorityGroup>()), it)
        } ?: listOf()

        val children = jsonElement["children"]?.jsonArray?.map { childArray ->
            childArray.jsonArray.map { it.jsonPrimitive.content }
        } ?: listOf()

        return when {
            code.matches(Regex(SKIP_INSTRUCTION_PATTERN)) -> {
                val reservedCode = code.toReservedCode()
                val nonNullableInput = condition ?: (returnType?.defaultTextValue() ?: reservedCode.defaultReturnType()
                    .defaultTextValue())

                SkipInstruction(
                    code = code,
                    skipToComponent = skipToComponent,
                    toEnd = toEnd,
                    condition = nonNullableInput,
                    text = text ?: nonNullableInput,
                    isActive = isActive ?: reservedCode.defaultIsActive(),
                    errors = errors
                )
            }

            code.isReservedCode() -> {
                val reservedCode = code.toReservedCode()
                SimpleState(
                    text = text ?: (returnType?.defaultTextValue() ?: reservedCode.defaultReturnType()
                        .defaultTextValue()),
                    reservedCode,
                    returnType = returnType ?: reservedCode.defaultReturnType(),
                    isActive = isActive ?: reservedCode.defaultIsActive(),
                    errors = errors
                )
            }

            code.matches(Regex(VALID_REFERENCE_INSTRUCTION_PATTERN)) -> {
                Reference(code, references, lang!!, errors)
            }

            code == Instruction.RANDOM_GROUP -> {
                RandomGroups(groups = groups, errors = errors)
            }

            code == Instruction.PRIORITY_GROUPS -> {
                PriorityGroups(priorities = priorities, errors = errors)
            }

            code == Instruction.PARENT_RELEVANCE -> {
                ParentRelevance(children = children, errors = errors)
            }

            else -> throw SerializationException("Invalid JSON for instruction")
        }
    }
}