package com.qlarr.surveyengine.navigate

import com.qlarr.surveyengine.context.indexableCodes
import com.qlarr.surveyengine.ext.flatten
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.ReservedCode.*
import com.qlarr.surveyengine.model.exposed.NavigationDirection
import com.qlarr.surveyengine.model.exposed.NavigationIndex
import com.qlarr.surveyengine.model.exposed.NavigationMode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive


fun Survey.navigate(
    navigationIndex: NavigationIndex? = null,
    navigationDirection: NavigationDirection,
    navigationMode: NavigationMode,
    navigationBindings: Map<Dependency, JsonElement>,
    skipInvalid: Boolean = false,
    currentIndexValid: Boolean = true,
): NavigationIndex {
    val newNavigationIndex = when (navigationDirection) {
        is NavigationDirection.Resume -> currentRelevant(navigationIndex!!, navigationMode, navigationBindings)
        NavigationDirection.Start -> firstRelevant(navigationMode, navigationBindings)
        is NavigationDirection.Next -> nextRelevant(navigationIndex!!, navigationMode, navigationBindings)
        is NavigationDirection.Jump -> navigationDirection.navigationIndex
        NavigationDirection.Previous -> prevRelevant(navigationIndex!!, navigationMode, navigationBindings)
    }
    // we can get the whole survey validity from the bindings...
    val surveyValid = navigationBindings[Dependency("Survey", Validity)]!!.jsonPrimitive.boolean
    val isSubmitting = navigationIndex is NavigationIndex.End
    val isNextOrJump = navigationDirection is NavigationDirection.Next
    val shouldNotSkipValid = !skipInvalid && isNextOrJump

    return when {
        (isSubmitting || shouldNotSkipValid) && !currentIndexValid -> {
            navigationIndex!!.with(true)
        }

        isSubmitting && !surveyValid -> firstInvalid(navigationMode, navigationBindings).with(true)
        else -> newNavigationIndex.with(false)
    }
}


fun Survey.componentsInCurrentNav(navigationIndex: NavigationIndex): List<String> {
    return when (navigationIndex) {
        is NavigationIndex.Question -> {
            val group = groups.first { group -> group.questions.any { it.code == navigationIndex.questionId } }
            return mutableListOf(group.code).apply {
                add(navigationIndex.questionId)
            }
        }

        is NavigationIndex.Groups -> {
            val returnList = mutableListOf<String>()
            val currentGroups = groups.filter { it.code in navigationIndex.groupIds }
            currentGroups.forEach { group ->
                returnList.add(group.code)
                returnList.addAll(group.questions.map { it.code })
            }
            returnList
        }

        is NavigationIndex.Group -> {
            val group = groups.first { it.code == navigationIndex.groupId }
            return mutableListOf(group.code).apply {
                addAll(group.questions.map { it.code })
            }
        }

        is NavigationIndex.End -> {
            val group = groups.first { it.code == navigationIndex.groupId }
            return mutableListOf(group.code).apply {
                addAll(group.questions.map { it.code })
            }
        }
    }
}


fun Survey.allNavigationCodes(): List<String> {
    val returnList = mutableListOf<String>()
    groups.forEach { group ->
        returnList.add(group.code)
        returnList.addAll(group.questions.map { it.code })
    }
    return returnList
}

fun Survey.navBefore(navigationIndex: NavigationIndex, bindings: Map<Dependency, JsonElement>): List<String> {
    val indexableCodes = children.indexableCodesRemoveDeprioritised(bindings)
    return when (navigationIndex) {
        is NavigationIndex.Groups -> {
            val minIndex = navigationIndex.groupIds.minOfOrNull { indexableCodes.indexOf(it) }!!
            indexableCodes.filterIndexed { index, _ -> index < minIndex }
        }

        is NavigationIndex.Group -> {
            val groupIndex = indexableCodes.indexOf(navigationIndex.groupId)
            indexableCodes.filterIndexed { index, _ -> index < groupIndex }
        }

        is NavigationIndex.Question -> {
            val questionIndex = indexableCodes.indexOf(navigationIndex.questionId)
            val groupIndex =
                indexableCodes.indexOfLast { code -> code.startsWith("G") && indexableCodes.indexOf(code) < questionIndex }
            indexableCodes.filterIndexed { index, code -> index < groupIndex || (code.startsWith("Q") && index < questionIndex) }
        }

        is NavigationIndex.End -> emptyList()
    }
}

fun Survey.navAfter(navigationIndex: NavigationIndex, bindings: Map<Dependency, JsonElement>): List<String> {
    val indexableCodes = children.indexableCodesRemoveDeprioritised(bindings)
    return when (navigationIndex) {
        is NavigationIndex.Groups -> {
            val lastGroupIndex = navigationIndex.groupIds.maxOfOrNull { indexableCodes.indexOf(it) }!!
            val nextGroupIndex =
                indexableCodes.indexOfFirst { code -> code.startsWith("G") && indexableCodes.indexOf(code) > lastGroupIndex }
            if (nextGroupIndex == -1) {
                emptyList()
            } else {
                indexableCodes.filterIndexed { index, _ -> index >= nextGroupIndex }
            }
        }

        is NavigationIndex.Group -> {
            val groupIndex = indexableCodes.indexOf(navigationIndex.groupId)
            val nextGroupIndex =
                indexableCodes.indexOfFirst { code -> code.startsWith("G") && indexableCodes.indexOf(code) > groupIndex }
            if (nextGroupIndex == -1) {
                emptyList()
            } else {
                indexableCodes.filterIndexed { index, _ -> index >= nextGroupIndex }
            }
        }

        is NavigationIndex.Question -> {
            val questionIndex = indexableCodes.indexOf(navigationIndex.questionId)
            indexableCodes.filterIndexed { index, _ -> index > questionIndex }
        }

        is NavigationIndex.End -> emptyList()
    }
}

fun List<SurveyComponent>.indexableCodesRemoveDeprioritised(bindings: Map<Dependency, JsonElement>): List<String> {
    return mapNotNull {
        if (it.noErrors() && it.hasUniqueCode()
            && (it !is Group || it.groupType != GroupType.END)
            && (!bindings.keys.contains(Dependency(it.code, Prioritised))
                    || bindings[Dependency(it.code, Prioritised)]!!.jsonPrimitive.boolean)
        )
            mutableListOf(it.code)
                .apply {
                    addAll(it.children.indexableCodes())
                }
        else
            null
    }
        .flatten()
}


fun Survey.allInOne(): NavigationIndex {
    return NavigationIndex.Groups(groups.filter { it.groupType != GroupType.END }.map { it.code })
}

private fun Survey.firstRelevant(
    navigationMode: NavigationMode,
    orderRelevanceBindings: Map<Dependency, JsonElement>
): NavigationIndex {
    return when (navigationMode) {
        NavigationMode.ALL_IN_ONE -> allInOne()

        NavigationMode.GROUP_BY_GROUP -> {
            NavigationIndex.Group(groups.first { orderRelevanceBindings.isRelevant(it.code) }.code)
        }

        NavigationMode.QUESTION_BY_QUESTION -> {
            val firstRelevantGroup = groups.first { orderRelevanceBindings.isRelevant(it.code) }
            return NavigationIndex.Question(firstRelevantGroup.questions.first { orderRelevanceBindings.isRelevant(it.code) }.code)
        }
    }
}

private fun Survey.firstInvalid(
    navigationMode: NavigationMode,
    orderRelevanceBindings: Map<Dependency, JsonElement>
): NavigationIndex {
    return when (navigationMode) {
        NavigationMode.ALL_IN_ONE -> {
            NavigationIndex.Groups(groups.filter { it.groupType != GroupType.END }.map { it.code })
        }

        NavigationMode.GROUP_BY_GROUP -> {
            NavigationIndex.Group(groups.first { orderRelevanceBindings.isInvalid(it.code) }.code)
        }

        NavigationMode.QUESTION_BY_QUESTION -> {
            val firstRelevantGroup = groups.first { orderRelevanceBindings.isInvalid(it.code) }
            return NavigationIndex.Question(firstRelevantGroup.questions.first { orderRelevanceBindings.isInvalid(it.code) }.code)
        }
    }
}

private fun Survey.currentRelevant(
    navigationIndex: NavigationIndex,
    navigationMode: NavigationMode,
    orderRelevanceBindings: Map<Dependency, JsonElement>
): NavigationIndex {
    val indexNavMode = navigationIndex.navigationMode()
    return if (navigationIndex is NavigationIndex.End || indexNavMode == navigationMode) {
        return navigationIndex
    } else when (navigationMode) {
        NavigationMode.ALL_IN_ONE -> NavigationIndex.Groups(
            groups
                .filter { it.groupType != GroupType.END }
                .map { it.code }
        )


        NavigationMode.GROUP_BY_GROUP -> {
            if (navigationIndex is NavigationIndex.Question) {
                NavigationIndex.Group(groups.first {
                    it.questions.map { it.code }.contains(navigationIndex.questionId)
                }.code)
            } else {
                // it cannot be GROUP_BY_GROUP or END
                // it has to be All in one
                NavigationIndex.Group(groups.first { orderRelevanceBindings.isRelevant(it.code) }.code)
            }

        }

        NavigationMode.QUESTION_BY_QUESTION -> {
            if (navigationIndex is NavigationIndex.Group) {
                NavigationIndex.Question(
                    groups.first { it.code == navigationIndex.groupId }
                        .questions.first { orderRelevanceBindings.isRelevant(it.code) }.code
                )
            } else {
                // it cannot be GROUP_BY_GROUP or END
                // it has to be All in one
                val firstRelevantGroup = groups.first { orderRelevanceBindings.isRelevant(it.code) }
                return NavigationIndex.Question(firstRelevantGroup.questions.first { orderRelevanceBindings.isRelevant(it.code) }.code)
            }
        }
    }
}

private fun Survey.nextRelevant(
    navigationIndex: NavigationIndex,
    navigationMode: NavigationMode,
    orderRelevanceBindings: Map<Dependency, JsonElement>
): NavigationIndex {
    return when (navigationMode) {
        NavigationMode.ALL_IN_ONE -> {
            NavigationIndex.End(groups.last().code)
        }

        NavigationMode.GROUP_BY_GROUP -> {
            val groupId = (navigationIndex as? NavigationIndex.Group)?.groupId ?: return firstRelevant(
                navigationMode,
                orderRelevanceBindings
            )
            val index = groups.indexOfFirst { it.code == groupId }
            val nextGroup = groups.subList(index + 1, groups.size).firstOrNull {
                orderRelevanceBindings.isRelevant(it.code) && it.hasRelevantChildren(orderRelevanceBindings)
            } ?: groups[index]
            if (nextGroup.groupType == GroupType.END || groups.indexOf(nextGroup) == index) {
                NavigationIndex.End(groups.last().code)
            } else {
                NavigationIndex.Group(nextGroup.code)
            }

        }

        NavigationMode.QUESTION_BY_QUESTION -> {
            val questionId =
                (navigationIndex as? NavigationIndex.Question)?.questionId ?: return firstRelevant(
                    navigationMode,
                    orderRelevanceBindings
                )
            val group = getGroup(questionId)
            val questionIndex = group.questions.indexOfFirst { it.code == questionId }
            val nextQuestion = group.questions.subList(questionIndex + 1, group.questions.size)
                .firstOrNull { orderRelevanceBindings.isRelevant(it.code) }
            return if (nextQuestion != null) {
                NavigationIndex.Question(nextQuestion.code)
            } else {
                val groupIndex = groups.indexOfFirst { it.code == group.code }
                val nextGroup = groups.subList(groupIndex + 1, groups.size)
                    .firstOrNull {
                        orderRelevanceBindings.isRelevant(it.code) && it.hasRelevantChildren(
                            orderRelevanceBindings
                        )
                    } ?: groups[groupIndex]
                if (nextGroup.groupType == GroupType.END || groups.indexOf(nextGroup) == groupIndex) {
                    NavigationIndex.End(groups.last().code)
                } else {
                    NavigationIndex.Question(nextGroup.questions.first { orderRelevanceBindings.isRelevant(it.code) }.code)
                }
            }
        }
    }
}

fun SurveyComponent.hasRelevantChildren(orderRelevanceBindings: Map<Dependency, JsonElement>): Boolean {
    return this.children.any { orderRelevanceBindings.isRelevant(it.code) }
}

private fun Survey.prevRelevant(
    navigationIndex: NavigationIndex,
    navigationMode: NavigationMode,
    orderRelevanceBindings: Map<Dependency, JsonElement>
): NavigationIndex {
    return when (navigationMode) {
        NavigationMode.ALL_IN_ONE -> allInOne()

        NavigationMode.GROUP_BY_GROUP -> {
            val groupId = (navigationIndex as? NavigationIndex.Group)?.groupId ?: return firstRelevant(
                navigationMode,
                orderRelevanceBindings
            )
            val index = children.indexOfFirst { it.code == groupId }
            val prevGroup = children.subList(0, index).lastOrNull {
                orderRelevanceBindings.isRelevant(it.code) && it.hasRelevantChildren(orderRelevanceBindings)
            }
            prevGroup?.let {
                NavigationIndex.Group(it.code)
            } ?: navigationIndex

        }

        NavigationMode.QUESTION_BY_QUESTION -> {
            val questionId =
                (navigationIndex as? NavigationIndex.Question)?.questionId ?: return firstRelevant(
                    navigationMode,
                    orderRelevanceBindings
                )
            val group = getGroup(questionId)
            val questionIndex = group.children.indexOfFirst { it.code == questionId }
            val prevQuestion = group.children.subList(0, questionIndex)
                .lastOrNull { orderRelevanceBindings.isRelevant(it.code) }
            return if (prevQuestion != null) {
                NavigationIndex.Question(prevQuestion.code)
            } else {
                val groupIndex = children.indexOfFirst { it.code == group.code }
                val nextGroup = children.subList(0, groupIndex)
                    .lastOrNull {
                        orderRelevanceBindings.isRelevant(it.code) && it.hasRelevantChildren(
                            orderRelevanceBindings
                        )
                    }
                    ?: return navigationIndex
                NavigationIndex.Question(nextGroup.children.last { orderRelevanceBindings.isRelevant(it.code) }.code)
            }
        }
    }
}

private fun Map<Dependency, JsonElement>.isRelevant(code: String): Boolean {
    return get(Dependency(code, Relevance))?.jsonPrimitive?.boolean ?: true
}

private fun Map<Dependency, JsonElement>.isInvalid(code: String): Boolean {
    return get(Dependency(code, Validity))?.jsonPrimitive?.boolean == false
}

private fun Survey.getGroup(questionCode: String): Group {
    return children.first { group ->
        group.children.any { it.code == questionCode }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun Survey.sortByOrder(orderBindings: Map<Dependency, JsonElement>): Survey {
    return copy(groups = (groups.toMutableList() as MutableList<SurveyComponent>)
        .apply { sortByOrder(code, orderBindings) } as List<Group>)
}

internal fun MutableList<SurveyComponent>.sortByOrder(
    parentCode: String = "",
    orderBindings: Map<Dependency, JsonElement>
) {
    sortBy { surveyComponent ->
        val code = surveyComponent.uniqueCode(parentCode)
        if (orderBindings.containsKey(Dependency(code, Order))) {
            orderBindings[Dependency(code, Order)]!!.jsonPrimitive.int
        } else {
            indexOf(surveyComponent) + 1
        }
    }
    forEachIndexed { index, surveyComponent ->
        val code = surveyComponent.uniqueCode(parentCode)
        set(index, surveyComponent.duplicate(children = surveyComponent.children.toMutableList().apply {
            sortByOrder(code, orderBindings)
        }))
    }
}