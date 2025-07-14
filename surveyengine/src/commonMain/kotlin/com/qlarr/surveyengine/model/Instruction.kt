@file:Suppress("SERIALIZER_TYPE_INCOMPATIBLE")

package com.qlarr.surveyengine.model

import com.qlarr.surveyengine.ext.VALID_REFERENCE_INSTRUCTION_PATTERN
import com.qlarr.surveyengine.ext.VALID_REFERENCE_PREFIX
import com.qlarr.surveyengine.ext.flatten
import com.qlarr.surveyengine.model.Instruction.*
import com.qlarr.surveyengine.model.adapters.InstructionSerializer
import com.qlarr.surveyengine.model.exposed.ReturnType
import kotlinx.serialization.Serializable


@Serializable(with = InstructionSerializer::class)
sealed class Instruction {
    abstract val code: String
    abstract val errors: List<InstructionError>
    abstract fun addError(error: InstructionError): Instruction
    fun noErrors() = errors.isEmpty()
    abstract fun clearErrors(): Instruction


    @Serializable(with = InstructionSerializer::class)
    data class Reference(
        override val code: String,
        val references: List<String>,
        val lang: String,
        override val errors: List<InstructionError> = listOf()
    ) : Instruction() {
        init {
            if (!code.matches(Regex(VALID_REFERENCE_INSTRUCTION_PATTERN))) {
                throw IllegalArgumentException("Instruction code: $code must start with $VALID_REFERENCE_PREFIX")
            }
        }

        fun text(): String {
            val grouped = references.map { it.split(".") }.groupBy { it[0] }
            return grouped.keys.joinToString(separator = ", ", prefix = "{", postfix = "}", transform = { key ->
                val variables = grouped[key]!!
                "$key: " + variables.joinToString(separator = ", ", prefix = "{", postfix = "}", transform = { list ->
                    "${list[1]} : ${list[0]}.${list[1]}"
                })
            })
        }

        fun runnableInstruction() = RunnableInstruction(code, text(), ReturnType.Map, true, errors)

        override fun addError(error: InstructionError) = copy(errors = errors.toMutableList().apply { add(error) })
        override fun clearErrors() = copy(errors = emptyList())
    }


    @Serializable
    data class RandomGroups(
        override val code: String = RANDOM_GROUP,
        val groups: List<RandomGroup> = listOf(),
        override val errors: List<InstructionError> = listOf()
    ) : Instruction() {

        constructor(groups: List<List<String>>) : this(
            code = RANDOM_GROUP,
            groups = groups.map { RandomGroup(it) }
        )

        override fun addError(error: InstructionError) = copy(errors = errors.toMutableList().apply { add(error) })
        override fun clearErrors() = copy(errors = emptyList())
    }

    @Serializable
    data class PriorityGroups(
        override val code: String = PRIORITY_GROUPS,
        val priorities: List<PriorityGroup> = listOf(),
        override val errors: List<InstructionError> = listOf()
    ) : Instruction() {
        override fun addError(error: InstructionError) = copy(errors = errors.toMutableList().apply { add(error) })
        override fun clearErrors() = copy(errors = emptyList())
    }

    @Serializable
    data class PriorityGroup(
        val weights: List<ChildPriority>,
        val limit: Int = weights.size - 1
    ) {
        constructor(codes: List<String>) : this(codes.map { ChildPriority(it) })
    }

    @Serializable
    data class RandomGroup(
        val codes: List<String>,
        val randomOption: RandomOption = RandomOption.RANDOM
    )

    @Serializable
    enum class RandomOption {
        RANDOM, ALPHA, FLIP
    }

    @Serializable
    enum class FlipDirection {
        ASCENDING, DESCENDING
    }


    @Serializable
    data class ChildPriority(val code: String, val weight: Float = 1F)

    @Serializable
    data class ParentRelevance(
        override val code: String = PARENT_RELEVANCE,
        val children: List<List<String>> = listOf(),
        override val errors: List<InstructionError> = listOf()
    ) : Instruction() {
        override fun addError(error: InstructionError) = copy(errors = errors.toMutableList().apply { add(error) })
        override fun clearErrors() = copy(errors = emptyList())
    }


    abstract class State(
        open val text: String,
        open val generated: Boolean,
        open val reservedCode: ReservedCode,
        open val returnType: ReturnType = reservedCode.defaultReturnType(),
        open val isActive: Boolean = reservedCode.defaultIsActive(),
        override val errors: List<InstructionError> = listOf()
    ) : Instruction() {
        override val code: String
            get() = reservedCode.code

        fun validate() {
            if (reservedCode != ReservedCode.Value && returnType != reservedCode.defaultReturnType()) {
                throw IllegalArgumentException("invalid ReturnType:$returnType for $reservedCode")
            }
        }

        fun runnableInstruction() = RunnableInstruction(code, text, returnType, isActive, errors)

        abstract fun withValidatedInstruction(validatedInstruction: RunnableInstruction): State
        abstract fun withValidatedText(validatedText: String): State

    }


    @Serializable(with = InstructionSerializer::class)
    data class SimpleState(
        override val text: String,
        override val reservedCode: ReservedCode,
        override val generated: Boolean = false,
        override val returnType: ReturnType = reservedCode.defaultReturnType(),
        override val isActive: Boolean = reservedCode.defaultIsActive(),
        override val errors: List<InstructionError> = listOf()
    ) : State(text = text, reservedCode = reservedCode, generated = generated) {
        init {
            validate()
        }

        override fun withValidatedInstruction(validatedInstruction: RunnableInstruction) = copy(
            text = validatedInstruction.text,
            isActive = validatedInstruction.isActive,
            errors = validatedInstruction.errors
        )

        override fun withValidatedText(validatedText: String) = copy(text = validatedText)


        override fun addError(error: InstructionError) = copy(errors = errors.toMutableList().apply { add(error) })
        override fun clearErrors() = copy(errors = emptyList())
        fun duplicate() = copy()
    }

    @Serializable(with = InstructionSerializer::class)
    data class SkipInstruction(
        val skipToComponent: String = "",
        val toEnd: Boolean = false,
        val condition: String = "true",
        override val code: String = "skip_to_$skipToComponent",
        override val text: String = condition,
        override val reservedCode: ReservedCode = ReservedCode.Skip(code),
        override val isActive: Boolean = reservedCode.defaultIsActive(),
        override val errors: List<InstructionError> = listOf()
    ) : State(reservedCode = reservedCode, text = condition, returnType = ReturnType.Boolean, generated = false) {
        init {
            validate()
            if (!code.matches(Regex(SKIP_INSTRUCTION_PATTERN))) {
                throw IllegalArgumentException("Invalid Skip Instruction code: $code")
            }
        }

        override fun withValidatedInstruction(validatedInstruction: RunnableInstruction) = copy(
            text = validatedInstruction.text,
            isActive = validatedInstruction.isActive,
            errors = validatedInstruction.errors
        )

        override fun withValidatedText(validatedText: String) = copy(condition = validatedText)


        override fun addError(error: InstructionError) = copy(errors = errors.toMutableList().apply { add(error) })
        override fun clearErrors() = copy(errors = emptyList())
        fun duplicate() = copy()
    }

    @Serializable
    data class RunnableInstruction(
        val code: String,
        val text: String,
        val returnType: ReturnType,
        val isActive: Boolean,
        val errors: List<InstructionError>
    )


    companion object {
        const val RANDOM_GROUP = "random_group"
        const val PRIORITY_GROUPS = "priority_groups"
        const val PARENT_RELEVANCE = "parent_relevance"
    }
}

fun List<Instruction>.filterNoErrors() = filter { instruction -> instruction.noErrors() }

@Serializable
data class ComponentInstruction(val componentCode: String, val instruction: RunnableInstruction)

internal fun SurveyComponent.randomGroups(options: List<RandomOption>): List<RandomGroup> {
    return instructionList.filterNoErrors().filterIsInstance<RandomGroups>()
        .firstOrNull()
        ?.groups
        ?.filter { options.contains(it.randomOption) }
        ?: listOf()
}


internal fun SurveyComponent.priorityGroups(): List<PriorityGroup> {
    return instructionList.filterNoErrors().filterIsInstance<PriorityGroups>().firstOrNull()?.priorities
        ?: listOf()
}

internal fun RandomGroups.codes(): Set<Set<String>> {
    return if (noErrors())
        groups.map { it.codes.toSet() }.toSet()
    else
        setOf()
}

internal fun PriorityGroups.codes(): Set<Set<String>> {
    return if (noErrors())
        priorities.map { priorityGroup -> priorityGroup.weights.map { it.code }.toSet() }.toSet()
    else
        setOf()
}

internal fun PriorityGroups.containingCode(code: String): PriorityGroup? {
    if (errors.isNotEmpty())
        return null
    priorities.forEach { priorityGroup ->
        if (priorityGroup.weights.any { it.code == code })
            return priorityGroup
    }
    return null
}

internal fun SurveyComponent.priorityGroupCodes(): List<String> {
    return priorityGroups().map { priorityGroup -> priorityGroup.weights.map { it.code } }.flatten()
}

