package com.qlarr.surveyengine.context.assemble

import com.qlarr.surveyengine.ext.VALID_ANSWER_CODE
import com.qlarr.surveyengine.ext.VALID_QUESTION_CODE
import com.qlarr.surveyengine.ext.flatten
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.Instruction.RandomOption.FLIP
import com.qlarr.surveyengine.model.Instruction.RandomOption.RANDOM

internal fun List<ChildlessComponent>.parents(code: String): List<String> {
    val childlessComponent = first { it.code == code }
    if (childlessComponent.parentCode.isEmpty()) {
        return emptyList()
    }
    val parent = first { it.code == childlessComponent.parentCode }
    return listOf(parent.code).plus(parents(parent.code))

}

internal fun List<ChildlessComponent>.commonParent(code1: String, code2: String): ChildlessComponent? {
    val parents1 = parents(code1)
    val parents2 = parents(code2)
    return parents1.firstOrNull { parents2.contains(it) }?.let { code ->
        first { it.code == code }
    }
}

internal fun MutableList<SurveyComponent>.correctInstruction(
    parents: List<String>,
    componentCode: String,
    reservedCode: ReservedCode,
    instructionText: String
) {
    if (parents.isEmpty()) {
        val index = indexOfFirst { it.code == componentCode }
        val component = get(index)
        val instruction = component.instructionList.first { it.code == reservedCode.code } as Instruction.State
        val newComponent = component.replaceOrAddInstruction(instruction.withValidatedText(instructionText))
        set(index, newComponent)
    } else {
        val index = indexOfFirst { it.code == parents.first() }
        val component = get(index)
        val newChildren = component.children.toMutableList().apply {
            correctInstruction(parents.takeLast(parents.size - 1), componentCode, reservedCode, instructionText)
        }
        set(index, component.duplicate(children = newChildren))
    }

}

internal fun List<SurveyComponent>.getSchema(
    parentCode: String = "",
    randomizedChildrenCodes: List<String> = listOf(),
    prioritisedChildrenCodes: List<String> = listOf(),
): List<ResponseField> {
    val returnList = mutableListOf<ResponseField>()
    forEach { component ->
        val code = component.uniqueCode(parentCode)
        if (randomizedChildrenCodes.contains(component.code))
            returnList.add(ResponseField(code, ColumnName.ORDER, ReturnType.INT))
        if (prioritisedChildrenCodes.contains(component.code))
            returnList.add(ResponseField(code, ColumnName.PRIORITY, ReturnType.INT))
        if (component.code.matches(Regex(VALID_QUESTION_CODE)) || component.code.matches(Regex(VALID_ANSWER_CODE))) {
            component.instructionList.firstOrNull { it is Instruction.State && it.reservedCode == ReservedCode.Value }
                ?.let {
                    returnList.add(
                        ResponseField(
                            code,
                            ColumnName.VALUE,
                            (it as Instruction.State).returnType
                        )
                    )
                }
        }
        returnList.addAll(
            component.children.getSchema(
                code,
                component.randomGroups(listOf(RANDOM, FLIP)).map { it.codes }.flatten(),
                component.priorityGroupCodes()
            )
        )
    }
    return returnList
}