package com.qlarr.surveyengine.model

import com.qlarr.surveyengine.model.exposed.ReturnType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ReservedCodeSerializer::class)
sealed class ReservedCode(
    open val code: String,
    val executionOrder: Int = 100,
    val isAccessible: Boolean = false,
    val accessibleByChildren: Boolean = false,
    // will ever have a state function that is runnable
    val isRuntime: Boolean = true,
    val requiresValidation: Boolean = false,
) {
    data object Lang :
        ReservedCode(
            "lang",
            executionOrder = 1,
            isAccessible = true,
            accessibleByChildren = true,
            isRuntime = false
        )

    data object Mode :
        ReservedCode(
            "mode",
            executionOrder = 1,
            isAccessible = true,
            accessibleByChildren = true,
            isRuntime = false
        )

    data object Prioritised : ReservedCode("prioritised", executionOrder = 1, isAccessible = true)

    data object NotSkipped : ReservedCode("not_skipped", executionOrder = 1, isAccessible = true)

    data object ConditionalRelevance :
        ReservedCode("conditional_relevance", executionOrder = 1, requiresValidation = true, isAccessible = true)

    data object ChildrenRelevance : ReservedCode("children_relevance", executionOrder = 1, requiresValidation = true, isAccessible = true)

    data object ModeRelevance : ReservedCode("mode_relevance", executionOrder = 1, requiresValidation = true, isAccessible = true)

    data object Relevance : ReservedCode("relevance", executionOrder = 2, true)

    data object Value : ReservedCode("value", executionOrder = 3, true, true, requiresValidation = true)

    data class ValidationRule(override val code: String) :
        ReservedCode(code, executionOrder = 5, requiresValidation = true)

    data object Validity : ReservedCode("validity", executionOrder = 6, true)

    data class Skip(override val code: String) : ReservedCode(code, executionOrder = 7, true)

    data object MaskedValue : ReservedCode("masked_value", isAccessible = true, requiresValidation = true)

    data object RelevanceMap : ReservedCode("relevance_map", executionOrder = 8)

    data object ValidityMap : ReservedCode("validity_map", executionOrder = 8)

    data object BeforeNavigation : ReservedCode("before_navigation", executionOrder = 8)

    data object AfterNavigation : ReservedCode("after_navigation", executionOrder = 8)

    data object Order : ReservedCode("order", isAccessible = true, requiresValidation = true)

    data object Priority : ReservedCode("priority", isAccessible = true, isRuntime = false)

    data object ShowErrors : ReservedCode("show_errors", isRuntime = false)

    data object HasPrevious : ReservedCode("has_previous")

    data object HasNext : ReservedCode("has_next")

    data object Meta : ReservedCode("meta", isRuntime = false)

    data object Label : ReservedCode("label", isRuntime = false, isAccessible = true, accessibleByChildren = true)

    data object InCurrentNavigation : ReservedCode("in_current_navigation", isRuntime = false, isAccessible = true)

    fun defaultReturnType(): ReturnType {
        return when (this) {
            is Order, is Priority -> ReturnType.INT
            is Meta, RelevanceMap, ValidityMap -> ReturnType.MAP
            is BeforeNavigation, AfterNavigation -> ReturnType.LIST
            is Lang, is Mode, is Value, is MaskedValue, is Label -> ReturnType.STRING
            is Relevance, is Prioritised, is NotSkipped, is ConditionalRelevance, is ModeRelevance,
            is ChildrenRelevance, is InCurrentNavigation, is Skip, is Validity, is ValidationRule, is ShowErrors,
            is HasPrevious, is HasNext -> ReturnType.BOOLEAN
        }
    }


    fun defaultIsActive(): Boolean {
        return when (this) {
            Order, Value, Meta, ShowErrors, Lang, Mode, Priority, Label, InCurrentNavigation -> false
            else -> true
        }
    }
}

class ReservedCodeSerializer : KSerializer<ReservedCode> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ReservedCode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ReservedCode) {
        encoder.encodeString(value.code)
    }

    override fun deserialize(decoder: Decoder): ReservedCode {
        return decoder.decodeString().toReservedCode()
    }
}


const val VALIDATION_PREFIX = "validation_"
const val VALIDATION_INSTRUCTION_PATTERN = "$VALIDATION_PREFIX[a-z0-9][a-z0-9_]*\$"
const val SKIP_PREFIX = "skip_to_"
const val SKIP_INSTRUCTION_PATTERN = "$SKIP_PREFIX[A-Za-z0-9][A-Za-z0-9_]*\$"

fun String.toReservedCode(): ReservedCode {
    return when {
        this == "lang" -> ReservedCode.Lang
        this == "mode" -> ReservedCode.Mode
        this == "prioritised" -> ReservedCode.Prioritised
        this == "not_skipped" -> ReservedCode.NotSkipped
        this == "order" -> ReservedCode.Order
        this == "priority" -> ReservedCode.Priority
        this == "meta" -> ReservedCode.Meta
        this == "show_errors" -> ReservedCode.ShowErrors
        this == "value" -> ReservedCode.Value
        this == "relevance" -> ReservedCode.Relevance
        this == "conditional_relevance" -> ReservedCode.ConditionalRelevance
        this == "children_relevance" -> ReservedCode.ChildrenRelevance
        this == "validity" -> ReservedCode.Validity
        this == "has_previous" -> ReservedCode.HasPrevious
        this == "has_next" -> ReservedCode.HasNext
        this == "masked_value" -> ReservedCode.MaskedValue
        this == "label" -> ReservedCode.Label
        this == "in_current_navigation" -> ReservedCode.InCurrentNavigation
        this == "before_navigation" -> ReservedCode.BeforeNavigation
        this == "after_navigation" -> ReservedCode.AfterNavigation
        this == "relevance_map" -> ReservedCode.RelevanceMap
        this == "validity_map" -> ReservedCode.ValidityMap
        this.matches(Regex(VALIDATION_INSTRUCTION_PATTERN)) -> ReservedCode.ValidationRule(this)
        this.matches(Regex(SKIP_INSTRUCTION_PATTERN)) -> ReservedCode.Skip(this)
        else -> throw IllegalStateException("")
    }
}

fun String.isReservedCode(): Boolean {
    return this in listOf(
        "lang",
        "mode",
        "prioritised",
        "not_skipped",
        "order",
        "priority",
        "meta",
        "show_errors",
        "value",
        "relevance",
        "children_relevance",
        "conditional_relevance",
        "validity",
        "has_previous",
        "has_next",
        "masked_value",
        "relevance_map",
        "validity_map",
        "label",
        "in_current_navigation",
        "before_navigation",
        "after_navigation"
    )
            || this.matches(Regex(VALIDATION_INSTRUCTION_PATTERN))
            || this.matches(Regex(SKIP_INSTRUCTION_PATTERN))
}


