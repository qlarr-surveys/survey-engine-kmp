package com.qlarr.surveyengine.context.assemble

import com.qlarr.surveyengine.ext.isSurveyCode
import com.qlarr.surveyengine.ext.splitToComponentCodes
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.exposed.ReturnType


private fun Survey.quotas() = instructionList
    .filterIsInstance<Instruction.State>()
    .filter { it.reservedCode is ReservedCode.Quota && it.noErrors() }

private fun Instruction.State.isQuotaMetCode() = IS_QUOTA_MET_SEGMENT + code.removePrefix(QUOTA_SEGMENT)

internal fun MutableList<SurveyComponent>.addQuotaInputs() {
    var survey = getOrNull(0) as? Survey ?: return
    survey.quotas().forEach { quota ->
        survey = survey.replaceOrAddInstruction(
            Instruction.SimpleState(
                text = "false",
                reservedCode = ReservedCode.IsQuotaMet(quota.isQuotaMetCode()),
                returnType = ReturnType.Boolean,
                isActive = false
            )
        ) as Survey
    }
    this[0] = survey
}

internal fun MutableList<SurveyComponent>.addQuotaSkipInstructions(dependencyMap: DependencyMap) {
    val survey = getOrNull(0) as? Survey ?: return
    val endGroupCode = survey.groups
        .firstOrNull { it.noErrors() && it.groupType == GroupType.END }
        ?.code ?: return
    val skipsByComponent = survey.quotas().flatMap { quota ->
        val skip = Instruction.SkipInstruction(
            skipToComponent = endGroupCode,
            disqualify = true,
            code = "skip_to_${quota.code}",
            text = "${survey.code}.${quota.code} && ${survey.code}.${quota.isQuotaMetCode()}"
        )
        dependencyMap[Dependent(survey.code, quota.code)].orEmpty()
            .map { it.componentCode.splitToComponentCodes().first() }
            .filterNot { it.isSurveyCode() }
            .distinct()
            .map { it to skip }
    }.groupBy({ it.first }, { it.second })
    if (skipsByComponent.isEmpty()) {
        return
    }
    this[0] = survey.withQuotaSkips(skipsByComponent)
}

private fun SurveyComponent.withQuotaSkips(
    skipsByComponent: Map<String, List<Instruction.SkipInstruction>>
): SurveyComponent {
    if (this is Group && groupType == GroupType.END) {
        return this
    }
    val withSkips = skipsByComponent[code].orEmpty()
        .fold(this) { component, skip -> component.replaceOrAddInstruction(skip) }
    return withSkips.duplicate(children = children.map { it.withQuotaSkips(skipsByComponent) })
}

internal fun SurveyComponent.removeQuotaSkipInstructions() = duplicate(
    instructionList = instructionList.filterNot {
        it is Instruction.SkipInstruction && it.code.startsWith("skip_to_$QUOTA_SEGMENT")
    }
)

internal fun SurveyComponent.removeQuotaInputs() = duplicate(
    instructionList = instructionList.filterNot {
        it is Instruction.State && it.reservedCode is ReservedCode.IsQuotaMet
    }
)
