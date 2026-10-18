package com.qlarr.surveyengine.context.assemble

import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.exposed.ReturnType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

internal fun MutableList<SurveyComponent>.addQuotaInputs() {
    var survey = getOrNull(0) as? Survey ?: return
    survey.quotas().forEach { quota ->
        survey = survey.replaceOrAddInstruction(
            Instruction.SimpleState(
                text = "false",
                reservedCode = quota.toIsMet(),
                returnType = ReturnType.Boolean,
                isActive = false
            )
        ) as Survey
    }
    this[0] = survey
}

internal fun Survey.screenOutQuota(bindings: Map<Dependency, JsonElement>): String? {
    fun isTrue(dependency: Dependency) = bindings[dependency]?.jsonPrimitive?.booleanOrNull == true
    return quotas().firstOrNull { quota ->
        isTrue(Dependency("Survey", quota.reservedCode)) && isTrue(Dependency("Survey", quota.toIsMet()))
    }?.code?.removePrefix(QUOTA_INSTRUCTION_PREFIX)
}

private fun Survey.quotas() = instructionList
    .filterIsInstance<Instruction.State>()
    .filter { it.reservedCode is ReservedCode.Quota && it.noErrors() }

private fun Instruction.State.toIsMet() = when (this.reservedCode) {
    is ReservedCode.Quota -> ReservedCode.IsQuotaMet(IS_QUOTA_MET_PREFIX + code.replace(QUOTA_INSTRUCTION_PREFIX, ""))
    else -> throw IllegalStateException("can only convert from Quota")
}

