package com.qlarr.surveyengine.context.assemble

import com.qlarr.surveyengine.ext.flatten
import com.qlarr.surveyengine.ext.splitToComponentCodes
import com.qlarr.surveyengine.model.*
import kotlinx.serialization.Serializable


fun List<ChildlessComponent>.skipManifesto(componentIndexList: List<ComponentIndex>)
        : Map<String, List<NotSkippedInstructionManifesto>> {
    val filteredComponentIndexList = componentIndexList.filter {
        it.parent == "Survey"
                || it.parent?.startsWith("G") ?: false
    }
    return map { component ->
        component.instructionList
            .filterIsInstance<Instruction.SkipInstruction>()
            .filter { it.errors.isEmpty() }
            .map { skipInstruction ->
                skipCodes(component.code, skipInstruction, filteredComponentIndexList)
                    .map {
                        it.withInstructionName(Dependency(component.code, skipInstruction.reservedCode))
                    }
            }.flatten()
    }.flatten()
        .groupBy { it.toBeSkippedCode }
}

fun List<SurveyComponent>.addSkipInstructions(map: Map<String, List<NotSkippedInstructionManifesto>>)
        : List<SurveyComponent> {
    val returnList = toMutableList()
    returnList.forEachIndexed { index, surveyComponent ->
        var newComponent = surveyComponent.duplicate()
        map[surveyComponent.code]?.let { instructionList ->
            if (instructionList.isNotEmpty()) {
                val text = instructionList.joinToString(separator = " && ", transform = {
                    it.text()
                })
                val notSkippedInstruction = Instruction.SimpleState(
                    text = text,
                    reservedCode = ReservedCode.NotSkipped,
                    isActive = true
                )
                newComponent = surveyComponent.replaceOrAddInstruction(notSkippedInstruction)
            }
        }
        if (newComponent is Group || newComponent is Survey) {
            newComponent = newComponent.duplicate(children = newComponent.children.addSkipInstructions(map))
        }
        returnList[index] = newComponent
    }
    return returnList
}


// only visible for testing
fun skipCodes(
    componentCode: String,
    skipInstruction: Instruction.SkipInstruction,
    componentIndexList: List<ComponentIndex>
)
        : List<SkipCodeOutput> {
    val fromComponent = componentCode.splitToComponentCodes()[0]
    val toComponent = skipInstruction.skipToComponent
    val fromAGroup = fromComponent.startsWith("G")
    val fromAQuestion = !fromAGroup
    val toAGroup = toComponent.startsWith("G")
    val toAQuestion = !toAGroup
    val fromComponentIndex = componentIndexList.first { it.code == fromComponent }
    val toComponentIndex = componentIndexList.first { it.code == toComponent }

    val fromGroup = if (fromAGroup)
        fromComponentIndex
    else
        componentIndexList.first { it.code == fromComponentIndex.parent }
    val fromQuestion = if (fromAGroup)
        null
    else
        fromComponentIndex

    val toGroup = if (toAGroup)
        toComponentIndex
    else
        componentIndexList.first { it.code == toComponentIndex.parent }

    val toQuestion = if (toAGroup)
        null
    else
        toComponentIndex

    val involvedGroups: List<SkipCodeOutput> = componentIndexList
        .filter {
            it.code.startsWith("G")
                    && it.code != fromGroup.code
                    && fromGroup.code != toGroup.code
                    && (skipInstruction.toEnd || it.code != toGroup.code)
                    && !fromGroup.prioritisedSiblings.contains(it.code)
                    && it.maxIndex >= fromGroup.minIndex
                    && it.minIndex <= toGroup.maxIndex
        }.map {
            val fromOrderNecessary = it.minIndex <= fromGroup.minIndex
                    || it.maxIndex <= fromGroup.maxIndex
            val toOrderNecessary = it != toGroup && (it.minIndex >= toGroup.minIndex
                    || it.maxIndex >= toGroup.maxIndex)
            SkipCodeOutput(
                toBeSkippedCode = it.code,
                fromOrderNecessary = fromOrderNecessary,
                toOrderNecessary = toOrderNecessary,
                fromComponent = if (fromOrderNecessary) fromGroup.code else "",
                toComponent = if (toOrderNecessary) toGroup.code else ""
            )
        }
    val involvedQuestions: List<SkipCodeOutput> = when {
        fromAQuestion && toAQuestion -> {
            if (fromGroup == toGroup) {
                componentIndexList
                    .filter {
                        it.parent == toQuestion!!.parent
                                && it.code != toQuestion.code
                                && it.code != fromQuestion!!.code
                                && !fromQuestion.prioritisedSiblings.contains(it.code)
                                && it.maxIndex >= fromQuestion.minIndex
                                && it.minIndex <= toQuestion.maxIndex
                    }.map {
                        val fromOrderNecessary = it.minIndex <= fromQuestion!!.minIndex
                                || it.maxIndex <= fromQuestion.maxIndex
                        val toOrderNecessary = it.minIndex >= toQuestion!!.minIndex
                                || it.maxIndex >= toQuestion.maxIndex
                        SkipCodeOutput(
                            toBeSkippedCode = it.code,
                            fromOrderNecessary = fromOrderNecessary,
                            toOrderNecessary = toOrderNecessary,
                            fromComponent = if (fromOrderNecessary) fromQuestion.code else "",
                            toComponent = if (toOrderNecessary) toQuestion.code else ""
                        )
                    }
            } else {
                componentIndexList
                    .filter {
                        (it.parent == fromQuestion!!.parent
                                && it.code != fromQuestion.code
                                && !fromQuestion.prioritisedSiblings.contains(it.code)
                                && it.maxIndex >= fromQuestion.minIndex)
                                || (it.parent == toQuestion!!.parent
                                && it.code != toQuestion.code
                                && it.minIndex <= toQuestion.maxIndex)
                    }.map {
                        val fromOrderNecessary = it.parent == fromQuestion!!.parent
                                && (it.minIndex <= fromQuestion.minIndex
                                || it.maxIndex <= fromQuestion.maxIndex)
                        val toOrderNecessary = it.parent == toQuestion!!.parent
                                && (it.minIndex >= toQuestion.minIndex
                                || it.maxIndex >= toQuestion.maxIndex)
                        SkipCodeOutput(
                            toBeSkippedCode = it.code,
                            fromOrderNecessary = fromOrderNecessary,
                            toOrderNecessary = toOrderNecessary,
                            fromComponent = if (fromOrderNecessary) fromQuestion.code else "",
                            toComponent = if (toOrderNecessary) toQuestion.code else ""
                        )
                    }
            }

        }

        fromAQuestion && toAGroup -> {
            componentIndexList
                .filter {
                    it.parent == fromQuestion!!.parent
                            // in case from question to the same group without toEnd
                            && (skipInstruction.toEnd || it.parent != toGroup.code)
                            && it.code != fromQuestion.code
                            && !fromQuestion.prioritisedSiblings.contains(it.code)
                            && it.maxIndex >= fromQuestion.minIndex
                }.map {
                    val fromOrderNecessary = it.minIndex <= fromQuestion!!.minIndex
                            || it.maxIndex <= fromQuestion.maxIndex
                    SkipCodeOutput(
                        toBeSkippedCode = it.code,
                        fromOrderNecessary = fromOrderNecessary,
                        toOrderNecessary = false,
                        fromComponent = if (fromOrderNecessary) fromQuestion.code else "",
                        toComponent = ""
                    )
                }
        }

        fromAGroup && toAQuestion -> {
            componentIndexList
                .filter {
                    it.parent == toQuestion!!.parent
                            // in case from group to a question inside group
                            && it.parent != fromGroup.code
                            && it.code != toQuestion.code
                            && it.minIndex <= toQuestion.maxIndex
                }.map {
                    val toOrderNecessary = it.minIndex >= toQuestion!!.minIndex
                            || it.maxIndex >= toQuestion.maxIndex
                    SkipCodeOutput(
                        toBeSkippedCode = it.code,
                        fromOrderNecessary = false,
                        toOrderNecessary = toOrderNecessary,
                        fromComponent = "",
                        toComponent = if (toOrderNecessary) toQuestion.code else ""
                    )
                }
        }
        //  fromAGroup && toAGroup
        else -> {
            listOf()
        }
    }
    return involvedGroups.toMutableList().apply { addAll(involvedQuestions) }
}

data class SkipCodeOutput(
    val toBeSkippedCode: String,
    val fromOrderNecessary: Boolean,
    val toOrderNecessary: Boolean,
    val fromComponent: String,
    val toComponent: String
) {
    fun withInstructionName(dependency: Dependency) = NotSkippedInstructionManifesto(
        dependency, fromComponent, toComponent, toBeSkippedCode, fromOrderNecessary, toOrderNecessary
    )
}

@Serializable
data class NotSkippedInstructionManifesto(
    val dependency: Dependency,
    val fromComponent: String = "",
    val toComponent: String = "",
    val toBeSkippedCode: String,
    val fromOrderNecessary: Boolean,
    val toOrderNecessary: Boolean
) {
    fun anyOrder() = fromOrderNecessary || toOrderNecessary
    private fun fromOrderText() = if (fromOrderNecessary) "$toBeSkippedCode.order < $fromComponent.order || " else ""
    private fun toOrderText() = if (toOrderNecessary) "$toBeSkippedCode.order > $toComponent.order || " else ""
    fun text() =
        "(${fromOrderText()}${toOrderText()}!${dependency.asCode()} || !${dependency.componentCode}.relevance)"

}