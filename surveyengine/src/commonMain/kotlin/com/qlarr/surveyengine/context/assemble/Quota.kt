package com.qlarr.surveyengine.context.assemble

import com.qlarr.surveyengine.dependency.toDependent
import com.qlarr.surveyengine.ext.splitToComponentCodes
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.exposed.NavigationIndex
import com.qlarr.surveyengine.model.exposed.ReturnType
import com.qlarr.surveyengine.navigate.navBefore
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

internal fun Survey.screenOutQuota(
    navigationIndex: NavigationIndex,
    bindings: Map<Dependency, JsonElement>,
    dependencyMap: DependencyMap
): String? {
    val passed = if (navigationIndex is NavigationIndex.End) null else navBefore(navigationIndex, bindings).toSet()
    fun isTrue(dependency: Dependency) = bindings[dependency]?.jsonPrimitive?.booleanOrNull == true
    fun isHidden(question: String) =
        bindings[Dependency(question, ReservedCode.Relevance)]?.jsonPrimitive?.booleanOrNull == false

    fun isSettled(question: String, seen: Set<String>): Boolean {
        if (passed == null || question in passed) return true
        if (!isHidden(question)) return false
        return dependencyMap.answeredQuestions(Dependent(question, ReservedCode.Relevance.code))
            .filter { it != question }
            .all { it !in seen && isSettled(it, seen + question) }
    }
    return quotas().firstOrNull { quota ->
        val met = Dependency("Survey", quota.reservedCode)
        isTrue(met)
                && isTrue(Dependency("Survey", quota.toIsMet()))
                && dependencyMap.answeredQuestions(met.toDependent()).all { isSettled(it, emptySet()) }
    }?.code?.removePrefix("quota_")
}

private fun Survey.quotas() = instructionList
    .filterIsInstance<Instruction.State>()
    .filter { it.reservedCode is ReservedCode.Quota && it.noErrors() }

private fun Instruction.State.toIsMet() = when (this.reservedCode) {
    is ReservedCode.Quota -> ReservedCode.IsQuotaMet(IS_QUOTA_MET_PREFIX + code.replace(QUOTA_INSTRUCTION_PREFIX, ""))
    else -> throw IllegalStateException("can only convert from Quota")
}


private fun DependencyMap.answeredQuestions(start: Dependent): Set<String> {
    val found = linkedSetOf<String>()
    val visited = mutableSetOf<Dependent>()
    fun walk(dependent: Dependent) {
        if (!visited.add(dependent)) return
        this[dependent]?.forEach { dependency ->
            if (dependency.reservedCode == ReservedCode.Value) {
                found.add(dependency.componentCode.splitToComponentCodes().first())
            } else {
                walk(dependency.toDependent())
            }
        }
    }
    walk(start)
    return found
}
