package com.qlarr.surveyengine.context.execute

import com.qlarr.surveyengine.ext.isUniqueCode
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.Instruction.*
import com.qlarr.surveyengine.model.Instruction.RandomOption.*
import com.qlarr.surveyengine.model.ReservedCode.Order
import com.qlarr.surveyengine.model.ReservedCode.Priority
import kotlin.math.absoluteValue
import kotlin.random.Random


@Suppress("UNCHECKED_CAST")
internal fun Survey.randomize(randomOptions: List<RandomOption>, getLabel: (String) -> String): Map<Dependency, Int> {
    return (mutableListOf(this) as MutableList<SurveyComponent>).randomizeChildren(
        getLabel = getLabel,
        randomOptions = randomOptions
    )
}

@Suppress("UNCHECKED_CAST")
internal fun Survey.setPriorities(): Map<Dependency, Int> {
    return (mutableListOf(this) as MutableList<SurveyComponent>).setChildrenPriorities()
}

internal fun List<SurveyComponent>.randomizeChildren(
    parentCode: String = "",
    pendulumDirection: FlipDirection = FlipDirection.entries[Random.nextInt().absoluteValue % 2],
    getLabel: (String) -> String = { "" },
    randomOptions: List<RandomOption> = entries
): Map<Dependency, Int> {
    val mutableMap = mutableMapOf<Dependency, Int>()
    forEach { surveyComponent ->
        if (surveyComponent.noErrors()) {
            val code = surveyComponent.uniqueCode(parentCode)
            surveyComponent.randomGroups(randomOptions).forEach {
                mutableMap.putAll(surveyComponent.children.randomizeChildren(code, it, pendulumDirection, getLabel))
            }
            mutableMap.putAll(
                surveyComponent.children.randomizeChildren(
                    code,
                    pendulumDirection,
                    getLabel,
                    randomOptions
                )
            )
        }
    }
    return mutableMap
}

internal fun List<SurveyComponent>.setChildrenPriorities(parentCode: String = ""): Map<Dependency, Int> {
    val mutableMap = mutableMapOf<Dependency, Int>()
    forEach { surveyComponent ->
        if (surveyComponent.noErrors()) {
            val code = surveyComponent.uniqueCode(parentCode)
            surveyComponent.priorityGroups().forEach {
                mutableMap.putAll(surveyComponent.children.prioritiseChildren(code, it))
            }
            mutableMap.putAll(surveyComponent.children.setChildrenPriorities(code))
        }
    }
    return mutableMap
}


private fun List<SurveyComponent>.randomizeChildren(
    parentCode: String,
    randomGroup: RandomGroup,
    pendulumDirection: FlipDirection,
    getLabel: (String) -> String
): Map<Dependency, Int> {
    val mutableMap = mutableMapOf<Dependency, Int>()
    val order = linkedMapOf<String, Int>()
    randomGroup.codes.forEach { item ->
        order[item] = 1 + indexOfFirst { surveyComponent ->
            surveyComponent.code == item
        }
    }
    val random: Map<String, Int> = when (randomGroup.randomOption) {
        ALPHA -> {
            val sortedKeys = order.keys.sortedBy { code ->
                val qualifiedCode = if (code.isUniqueCode()) code else parentCode + code
                getLabel(qualifiedCode)
            }
            val sortedValues = order.values.sorted()

            sortedKeys.zip(sortedValues).toMap(linkedMapOf())
        }

        RANDOM -> {
            val sortedKeys = order.keys
            val sortedValues = order.values.shuffled()
            sortedKeys.zip(sortedValues).toMap(linkedMapOf())
        }
        FLIP -> {
            val sortedKeys = order.keys
            val sortedValues = order.values.let {
                if (pendulumDirection == FlipDirection.DESCENDING){
                    it.reversed()
                } else {
                    it
                }
            }
            sortedKeys.zip(sortedValues).toMap(linkedMapOf())
        }
    }


    random.forEach { entry ->
        val component = first { it.code == entry.key }
        val code = component.uniqueCode(parentCode)
        mutableMap[Dependency(code, Order)] = entry.value
    }

    return mutableMap
}

private fun List<SurveyComponent>.prioritiseChildren(
    parentCode: String,
    priorityGroup: PriorityGroup
): Map<Dependency, Int> {
    val mutableMap = mutableMapOf<Dependency, Int>()
    val weights: List<Float> = priorityGroup.weights.map {
        val random = (1..priorityGroup.limit * 100)
        it.weight * random.random()
    }
    priorityGroup.weights
        .map { Pair(it.code, priorityGroup.weights.indexOf(it)) }
        .sortedBy { weights[it.second] }
        .forEachIndexed { index, pair ->
            val component = first { it.code == pair.first }
            val code = component.uniqueCode(parentCode)
            mutableMap[Dependency(code, Priority)] = index + 1
        }
    return mutableMap
}