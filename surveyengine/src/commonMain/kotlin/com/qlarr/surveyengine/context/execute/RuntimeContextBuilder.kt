package com.qlarr.surveyengine.context.execute

import com.qlarr.surveyengine.model.exposed.NavigationIndex
import com.qlarr.surveyengine.model.Dependency
import com.qlarr.surveyengine.model.Dependent
import com.qlarr.surveyengine.model.ReservedCode.*
import com.qlarr.surveyengine.model.Survey
import com.qlarr.surveyengine.navigate.allNavigationCodes
import com.qlarr.surveyengine.navigate.componentsInCurrentNav
import com.qlarr.surveyengine.navigate.navAfter
import com.qlarr.surveyengine.navigate.navBefore

internal class RuntimeContextBuilder(
    private val bindings: Map<Dependency, Any>,
    val impactMap: MutableMap<Dependency, List<Dependent>>,
    val dependencyMap: MutableMap<Dependent, List<Dependency>>
) {

    fun addValidityInstruction(survey: Survey, navigationIndex: NavigationIndex): Map<Dependency, Any> {
        // we already have a validity instructions with the right (actually all) dependencies
        // we need to actually
        // 1 update InCurrentNavigation for the components that will be part of next Nav
        // 2 update the validity value itself, so it is correct on the survey UI
        // update the other Group validity right here
        val returnBindings = mutableMapOf<Dependency, Any>()

        survey.allNavigationCodes().forEach {
            returnBindings[Dependency(it, InCurrentNavigation)] = false
        }
        val componentCodesInCurrentNav = survey.componentsInCurrentNav(navigationIndex)
        componentCodesInCurrentNav.forEach {
            returnBindings[Dependency(it, InCurrentNavigation)] = true
        }
        returnBindings[Dependency("Survey", Validity)] = componentCodesInCurrentNav
            // only QuestionCodes
            .filter { it.startsWith("Q") && bindings[Dependency(it, Relevance)] as Boolean }
            .map { bindings[Dependency(it, Validity)] as Boolean }
            .all { it }
        // also update group validity
        survey.children.filter { it.code in componentCodesInCurrentNav }.forEach { group ->
            group.children
                .filter { it.code in componentCodesInCurrentNav && bindings[Dependency(it.code, Relevance)] as Boolean }
                .map { bindings[Dependency(it.code, Validity)] as Boolean }
                .all { it }.let { result ->
                    returnBindings[Dependency(group.code, Validity)] = result
                }
        }
        return returnBindings
    }

    fun addShowErrorsInstruction(survey: Survey, isSurveyValid: Boolean): Map<Dependency, Any> {
        val returnBindings = mutableMapOf<Dependency, Any>()
        returnBindings[Dependency(survey.code, ShowErrors)] = !isSurveyValid
        return returnBindings

    }

    fun addBeforeAfterNav(survey: Survey, navigationIndex: NavigationIndex): Map<Dependency, Any> {
        val returnBindings = mutableMapOf<Dependency, Any>()
        survey.navBefore(navigationIndex, bindings).let { list ->
            returnBindings[Dependency("Survey", BeforeNavigation)] = list
            returnBindings[Dependency("Survey", HasPrevious)] =
                list.map { bindings[Dependency(it, Relevance)] as Boolean }.any { it }
        }
        survey.navAfter(navigationIndex, bindings).let { list ->
            returnBindings[Dependency("Survey", AfterNavigation)] = list
            returnBindings[Dependency("Survey", HasNext)] =
                list.map { bindings[Dependency(it, Relevance)] as Boolean }.any { it }
        }
        return returnBindings
    }


}
