package com.qlarr.surveyengine.context.assemble

import com.qlarr.surveyengine.dependency.toDependent
import com.qlarr.surveyengine.ext.splitToComponentCodes
import com.qlarr.surveyengine.model.*

const val QUOTA_SKIP_PREFIX = "skip_to_quota_"
private val QUOTA_CODE = Regex("^[A-Za-z0-9][A-Za-z0-9_]*$")

fun quotaSkipCode(quotaCode: String) = "$QUOTA_SKIP_PREFIX$quotaCode"
private fun quotaMetCode(quotaCode: String) = "var_${quotaCode}_met"
private fun quotaFullCode(quotaCode: String) = "var_${quotaCode}_full"

internal fun Instruction.isQuotaSkip() = code.startsWith(QUOTA_SKIP_PREFIX)

/**
 * Screens the respondent out once a full quota is met. Each quota (with a met variable on Survey)
 * gets a disqualifying skip to the end group on the last question its met variable depends on,
 * directly or through other instructions, or on the first question if it depends on none.
 * The skip waits until every question whose value the quota reads is answered or hidden by a display
 * condition (its own or its group's), so a condition that holds while a question is still unanswered
 * (e.g. `Q1.value != "a"`) can't screen out early.
 * A skip on a hidden question never fires, so an earlier dependency also gets the skip when every
 * later one can be hidden, firing only while they all are (a copy that could fire with a later one
 * shown would skip over it, emptying the page under the respondent).
 * Hidden can't mean `relevance`: skips feed into relevance, so that would be a cycle. A question
 * jumped over by another skip therefore never counts as hidden.
 * Quota skips are always rebuilt here: any coming in with the design are dropped.
 */
internal fun MutableList<SurveyComponent>.addQuotaSkipInstructions(
    quotaCodes: List<String>,
    dependencyMap: DependencyMap
) {
    var survey = (getOrNull(0) as? Survey)?.withoutQuotaSkips() as? Survey ?: return
    val endGroupCode = survey.groups.firstOrNull { it.groupType == GroupType.END && it.noErrors() }?.code ?: ""
    val questionCodes = survey.groups
        .filter { it.groupType != GroupType.END }
        .flatMap { group -> group.questions.map { it.code } }
    val surveyInstructions = survey.instructionList.map { it.code }.toSet()
    val displayConditions = survey.groups.flatMap { group ->
        group.questions.map { question ->
            question.code to listOf(group, question)
                .filter { it.hasDisplayCondition() }
                .map { "!${it.code}.${ReservedCode.ConditionalRelevance.code}" }
        }
    }.toMap()

    quotaCodes
        .distinct()
        .filter { QUOTA_CODE.matches(it) && quotaMetCode(it) in surveyInstructions }
        .forEach { quotaCode ->
            val dependencies = dependencyMap.questionDependencies(Dependent("Survey", quotaMetCode(quotaCode)))
            val questionOf = { dependency: Dependency -> dependency.componentCode.splitToComponentCodes().first() }
            val dependentQuestions = dependencies.map(questionOf).toSet()
            val targets = questionCodes.filter { it in dependentQuestions }
                .ifEmpty { listOfNotNull(questionCodes.firstOrNull()) }
            val hiddenExpression = { question: String -> "(${displayConditions[question]!!.joinToString(" || ")})" }
            val answered = dependencies
                .filter { it.reservedCode == ReservedCode.Value }
                .sortedBy { questionCodes.indexOf(questionOf(it)) }
                .map { dependency ->
                    val code = dependency.componentCode
                    val answered = "($code.value != null && $code.value.length !== 0)"
                    val hidden = displayConditions[questionOf(dependency)].orEmpty()
                    if (hidden.isEmpty()) answered else "(${hidden.joinToString(" || ")} || $answered)"
                }
            val condition = listOf("Survey.${quotaMetCode(quotaCode)}", "Survey.${quotaFullCode(quotaCode)}") + answered
            targets.forEachIndexed { index, target ->
                val later = targets.drop(index + 1)
                if (later.any { displayConditions[it].isNullOrEmpty() }) return@forEachIndexed
                val skip = Instruction.SkipInstruction(
                    code = quotaSkipCode(quotaCode),
                    skipToComponent = endGroupCode,
                    toEnd = false,
                    disqualify = true,
                    text = (condition + later.map(hiddenExpression)).joinToString(" && "),
                    isActive = true
                )
                survey = survey.withQuestionInstruction(target, skip)
            }
        }
    this[0] = survey
}

// Question-level dependencies (a question or its answers) reached from `start`, following
// dependencies on anything else (Survey variables, groups) down to the questions they read.
private fun DependencyMap.questionDependencies(start: Dependent): Set<Dependency> {
    val found = linkedSetOf<Dependency>()
    val visited = mutableSetOf<Dependent>()
    fun walk(dependent: Dependent) {
        if (!visited.add(dependent)) return
        this[dependent]?.forEach { dependency ->
            if (dependency.componentCode.startsWith("Q")) {
                found.add(dependency)
            } else {
                walk(dependency.toDependent())
            }
        }
    }
    walk(start)
    return found
}

private fun SurveyComponent.hasDisplayCondition() = instructionList.any {
    it.code == ReservedCode.ConditionalRelevance.code && it.noErrors()
}

private fun SurveyComponent.withoutQuotaSkips(): SurveyComponent = duplicate(
    instructionList = instructionList.filterNot { it.isQuotaSkip() },
    children = children.map { it.withoutQuotaSkips() }
)

private fun Survey.withQuestionInstruction(questionCode: String, instruction: Instruction): Survey = duplicate(
    children = groups.map { group ->
        group.duplicate(children = group.questions.map { question ->
            if (question.code == questionCode) question.replaceOrAddInstruction(instruction) else question
        })
    }
) as Survey
