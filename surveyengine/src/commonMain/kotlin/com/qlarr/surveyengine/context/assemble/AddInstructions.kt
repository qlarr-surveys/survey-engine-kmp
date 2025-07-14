package com.qlarr.surveyengine.context.assemble

import com.qlarr.surveyengine.context.*
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.Instruction.ParentRelevance
import com.qlarr.surveyengine.model.ReservedCode.*
import com.qlarr.surveyengine.model.exposed.ReturnType

internal fun MutableList<SurveyComponent>.addPreviousNextInstruction() {
    if (size == 1 && get(0) is Survey) {

        val indexableCodes = get(0).children.indexableCodes()
        val relevanceMapText = indexableCodes.joinToString(
            prefix = "{",
            postfix = "}",
            separator = ",",
            transform = {
                "$it: $it.relevance"
            }
        )
        val validityMapText = indexableCodes.joinToString(
            prefix = "{",
            postfix = "}",
            separator = ",",
            transform = {
                "$it: $it.validity"
            }
        )
        val survey = get(0)
            .insertOrOverrideState(RelevanceMap, relevanceMapText, true)
            .insertOrOverrideState(ValidityMap, validityMapText, true)
            .insertOrOverrideState(
                HasPrevious, "(function(){if(!Survey.before_navigation||!Survey.relevance_map){return false;}" +
                        "for(var i=0;i<Survey.before_navigation.length;i++){if(Survey.relevance_map[Survey.before_navigation[i]]){return true}}" +
                        "return false;})()", true
            )
            .insertOrOverrideState(
                HasNext, "(function(){if(!Survey.after_navigation||!Survey.relevance_map){return false;}" +
                        "for(var i=0;i<Survey.after_navigation.length;i++){if(Survey.relevance_map[Survey.after_navigation[i]]){return true}}" +
                        "return false;})()", true
            )
        clear()
        add(survey)
    }
}

internal fun MutableList<SurveyComponent>.addNotSkippedInstructions(componentIndexList: List<ComponentIndex>)
        : Map<String, List<NotSkippedInstructionManifesto>> {
    if (size == 1 && get(0) is Survey) {
        val map = (get(0) as Survey).nestedComponents().skipManifesto(componentIndexList)
        val newList = addSkipInstructions(map)
        clear()
        addAll(newList)
        return map
    }
    return mapOf()
}

internal fun MutableList<SurveyComponent>.adjustRelevanceInstruction(
    parentCode: String = "",
    parentHasRelevance: Boolean = false
) {
    forEachIndexed { index, surveyComponent ->
        if (surveyComponent.noErrors()) {
            set(index, surveyComponent.adjustRelevanceInstruction(parentCode, parentHasRelevance))
        }

    }
}

internal fun SurveyComponent.adjustRelevanceInstruction(
    parentCode: String,
    parentHasRelevance: Boolean
): SurveyComponent {
    val code = uniqueCode(parentCode)
    var text = if (this is Survey) "" else "true"
    if (hasConsiderableRelevance(ConditionalRelevance)) {
        text = "$code.conditional_relevance"
    }
    if (hasConsiderableRelevance(ChildrenRelevance)) {
        text = if (text == "true") "$code.children_relevance" else "$text && $code.children_relevance"
    }
    if (hasConsiderableRelevance(ModeRelevance)) {
        text = if (text == "true") "$code.mode_relevance" else "$text && $code.mode_relevance"
    }
    if (hasConsiderableRelevance(NotSkipped)) {
        text = if (text == "true") "$code.not_skipped" else "$text && $code.not_skipped"
    }
    if (hasConsiderableRelevance(Prioritised)) {
        text = if (text == "true") "$code.prioritised" else "$text && $code.prioritised"
    }
    if (parentHasRelevance) {
        text = if (text == "true") "$parentCode.relevance" else "$parentCode.relevance && $text"
    }

    val returnComponent = if (text.isEmpty() || text == "true") {
        this
    } else {
        insertOrOverrideState(Relevance, text, true)
    }
    val hasConsiderableRelevance = returnComponent.hasConsiderableRelevance(Relevance)

    return returnComponent.duplicate(children = returnComponent.children.toMutableList().apply {
        adjustRelevanceInstruction(code, hasConsiderableRelevance)
    })

}


internal fun MutableList<SurveyComponent>.addModeRelevanceInstruction() {
    forEachIndexed { index, surveyComponent ->
        if (surveyComponent.hasErrors()) {
            return@forEachIndexed
        }
        var returnComponent = surveyComponent.duplicate(
            children = surveyComponent.children.toMutableList().apply { addModeRelevanceInstruction() })

        if (returnComponent !is Survey && returnComponent.hasMode()) {
            val text = "Survey.mode === \"${returnComponent.mode()}\""
            returnComponent = returnComponent.insertOrOverrideState(Relevance, text, true)
        }


        set(index, returnComponent)
    }
}

internal fun MutableList<SurveyComponent>.addParentRelevanceInstruction(parentCode: String = "") {
    forEachIndexed { index, surveyComponent ->
        if (surveyComponent.hasErrors()) {
            return@forEachIndexed
        }
        val code = surveyComponent.uniqueCode(parentCode)
        var returnComponent = surveyComponent.duplicate(
            children = surveyComponent.children.toMutableList().apply { addParentRelevanceInstruction(code) })
        // we will not add Parent relevance to end groups
        if (returnComponent is Group
            && returnComponent.groupType != GroupType.END
        ) {
            returnComponent =
                returnComponent.addParentRelevanceInstruction(
                    code,
                    listOf(returnComponent.children.map { it.code })
                )
        } else {
            returnComponent.instructionList.filterNoErrors()
                .filterIsInstance<ParentRelevance>()
                .firstOrNull()?.let {
                    returnComponent = returnComponent.addParentRelevanceInstruction(code, it.children)
                }
        }
        set(index, returnComponent)
    }
}

private fun SurveyComponent.addParentRelevanceInstruction(
    parentCode: String,
    parentRelevanceChildren: List<List<String>>
): SurveyComponent {
    var relevanceText = ""
    parentRelevanceChildren.forEach { list ->
        val relevantChildren = children.filter { list.contains(it.code) }

        // If the ALL children are either hidden, with false instruction or have active instructions, then we add
        // parent relevance instruction
        if (relevantChildren.all { it.hasConsiderableRelevanceAsChild() }) {
            val additionalStatement = relevantChildren.filter {
                (it.hasConsiderableRelevanceAsChild())
            }.joinToString(separator = " || ", transform = {
                val code = it.uniqueCode(parentCode)
                val reservedCodes = it.getConsiderableRelevanceAsChild()

                reservedCodes.joinToString(prefix = if (reservedCodes.size > 1) "(" else "",
                    postfix = if (reservedCodes.size > 1) ")" else "",
                    separator = " && ",
                    transform = { reservedCode ->
                        "$code.${reservedCode.code}"
                    })
            })
            relevanceText =
                if (relevanceText.isEmpty()) additionalStatement else "$relevanceText || $additionalStatement"
        }
    }
    return if (relevanceText.isNotEmpty()) {
        insertOrOverrideState(ChildrenRelevance, relevanceText, true)
    } else {
        this
    }
}

private fun MutableList<SurveyComponent>.addPrioritisedInstruction(
    parentCode: String,
    priorityGroups: Instruction.PriorityGroups
) {
    if (priorityGroups.errors.isNotEmpty()) {
        return
    }
    forEachIndexed { index, surveyComponent ->
        val hasUniqueCode = surveyComponent.elementType.hasUniqueCode()
        val code = surveyComponent.uniqueCode(parentCode)
        priorityGroups.containingCode(surveyComponent.code)?.let { priorityGroup ->
            val text = priorityGroup.weights
                .filter { weight ->
                    weight.code != surveyComponent.code
                }.joinToString(separator = ", ",
                    prefix = "[",
                    postfix = "].filter(Boolean).length < ${priorityGroup.weights.count() + priorityGroup.limit - 1} - $code.priority",
                    transform = { "${if (hasUniqueCode) it.code else parentCode + it.code}.relevance" })
            set(index, surveyComponent.insertOrOverrideState(Prioritised, text, true))
        }
    }
}


internal fun MutableList<SurveyComponent>.addPrioritisedInstruction(parentCode: String) {
    forEachIndexed { index, surveyComponent ->
        var newComponent = surveyComponent.duplicate()
        val componentCode = surveyComponent.uniqueCode(parentCode)
        surveyComponent.instructionList.firstOrNull { it is Instruction.PriorityGroups }?.let {
            newComponent = newComponent.duplicate(children = surveyComponent.children.toMutableList().apply {
                addPrioritisedInstruction(componentCode, it as Instruction.PriorityGroups)
            })
        }
        newComponent = newComponent.duplicate(
            children = newComponent.children.toMutableList().apply { addPrioritisedInstruction(parentCode) })
        set(index, newComponent)
    }
}


internal fun MutableList<SurveyComponent>.addValidityInstructions() {
    forEachIndexed { index, surveyComponent ->
        if (surveyComponent.hasErrors()) {
            return@forEachIndexed
        }
        set(
            index,
            surveyComponent.addValidityInstructions()
        )
    }
}

private fun SurveyComponent.addValidityInstructions(parentCode: String = ""): SurveyComponent {
    val qualifiedCode = uniqueCode(parentCode)
    var validationText = ""
    var returnComponent =
        duplicate(
            children = if (children.isNotEmpty()) children.map { it.addValidityInstructions(qualifiedCode) } else listOf())
    // we do not add validity instructions to End Groups
    if (this is Survey || (this is Group && groupType != GroupType.END)) {
        validationText = children.withoutErrors().joinToString(
            postfix = "",
            separator = " && ",
            transform = {
                "(!${it.uniqueCode(qualifiedCode)}.in_current_navigation || !${it.uniqueCode(qualifiedCode)}.relevance || ${
                    it.uniqueCode(qualifiedCode)
                }.validity)"
            })
    } else if (this is Question || this is Answer) {
        if (hasEnumRule() && enumValues().isNotEmpty()) {
            returnComponent = insertOrOverrideState(
                reservedCode = ValidationRule(code = "validation_enum"),
                text = "QlarrScripts.isNotVoid(${qualifiedCode}.value) && ${enumValues()}.indexOf(${qualifiedCode}.value) == -1",
                isActive = true,
                returnType = ReturnType.Boolean
            )
        }
        if (returnComponent.hasActiveValidationRules()) {
            val validationRules = returnComponent.getActiveValidationRules()
            validationText = validationRules.joinToString(
                prefix = if (validationRules.size > 1) "(" else "",
                postfix = if (validationRules.size > 1) ")" else "",
                separator = " && ",
                transform = { "!$qualifiedCode.${it.code}" }
            )
        }
        val childrenWithValidity = returnComponent.children.filter {
            it.getStateInstruction(Validity)?.let { validityInstruction ->
                validityInstruction.text != "true"
            } ?: false
        }
        validationText = if (childrenWithValidity.isEmpty()) validationText else
            childrenWithValidity.joinToString(
                prefix = if (validationText.isEmpty()) "" else "$validationText && ",
                postfix = "",
                separator = " && ",
                transform = {
                    "(!${it.uniqueCode(qualifiedCode)}.relevance || ${it.uniqueCode(qualifiedCode)}.validity)"
                })
    }
    return if (validationText.isNotEmpty()) {
        returnComponent.insertOrOverrideState(Validity, validationText, true)
    } else {
        returnComponent
    }
}

internal fun MutableList<SurveyComponent>.addStateToAllComponents() {
    forEachIndexed { index, surveyComponent ->
        if (surveyComponent.hasErrors()) {
            return@forEachIndexed
        }
        val newComponent = when (surveyComponent) {
            is Survey -> {
                surveyComponent
                    .replaceOrAddInstruction(Instruction.SimpleState("en", Lang))
                    .insertOrOverrideState(Mode, "mixed", false)
                    .insertOrOverrideState(RelevanceMap, "{}", false)
                    .insertOrOverrideState(ValidityMap, "{}", false)
                    .insertOrOverrideState(BeforeNavigation, "[]", false)
                    .insertOrOverrideState(AfterNavigation, "[]", false)
                    .insertOrOverrideState(HasPrevious, "false", false)
                    .insertOrOverrideState(HasNext, "false", false)
            }

            else -> {
                surveyComponent
                    .insertOrOverrideState(Label, "", false)
                    .insertOrOverrideState(Relevance, "true", false)
                    .insertOrOverrideState(Validity, "true", false)
                    .insertOrOverrideState(InCurrentNavigation, "true", false)
                    .removeStateIfNotActive(Order)
                    .addOrder(index)
                    .removeState(Prioritised)
                    .removeState(ChildrenRelevance)
                    .removeState(ValidationRule("validation_enum"))
                    .removeState(ModeRelevance)
                    .removeState(NotSkipped)
                    .insertOrOverrideState(Priority, index.toString(), false)
            }
        }
        set(
            index,
            newComponent.duplicate(
                children = surveyComponent.children.toMutableList().apply { addStateToAllComponents() })
        )
    }
}

private fun SurveyComponent.addOrder(index: Int): SurveyComponent {
    return if (hasStateInstruction(Order)) {
        this
    } else {
        insertOrOverrideState(Order, index.toString(), false)
    }
}

private fun SurveyComponent.hasConsiderableRelevance(relevanceReservedCode: ReservedCode): Boolean {
    return instructionList.any {
        it.noErrors()
                && it.code == relevanceReservedCode.code
                && (it as Instruction.State).text != "true"
    }

}

private fun SurveyComponent.hasMode(): Boolean {
    return instructionList.any {
        it.noErrors()
                && it.code == Mode.code
    }

}

private fun SurveyComponent.mode(): String {
    return (instructionList.first {
        it.code == Mode.code
    } as Instruction.State).text

}

private fun SurveyComponent.hasConsiderableRelevanceAsChild(): Boolean {
    return hasConsiderableRelevance(ConditionalRelevance)
            || hasConsiderableRelevance(ChildrenRelevance)
            || hasConsiderableRelevance(ModeRelevance)
            || hasConsiderableRelevance(NotSkipped)
}

private fun SurveyComponent.getConsiderableRelevanceAsChild(): List<ReservedCode> {
    return instructionList
        .asSequence()
        .filter { it.noErrors() }
        .filterIsInstance<Instruction.State>()
        .filter {
            it.reservedCode in listOf(ConditionalRelevance, ChildrenRelevance, NotSkipped, ModeRelevance)
        }
        .filter { it.text != "true" }
        .map { it.reservedCode }
        .toList()

}