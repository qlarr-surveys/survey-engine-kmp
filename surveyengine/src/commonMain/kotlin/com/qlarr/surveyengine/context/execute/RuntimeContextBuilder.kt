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
import kotlinx.serialization.json.*

internal class RuntimeContextBuilder(
    private val bindings: Map<Dependency, JsonElement>,
    val impactMap: MutableMap<Dependency, List<Dependent>>,
    val dependencyMap: MutableMap<Dependent, List<Dependency>>
) {

    fun addValidityInstruction(survey: Survey, navigationIndex: NavigationIndex): Map<Dependency, JsonElement> {
        // we already have a validity instructions with the right (actually all) dependencies
        // we need to actually
        // 1 update InCurrentNavigation for the components that will be part of next Nav
        // 2 update the validity value itself, so it is correct on the survey UI
        // update the other Group validity right here
        val returnBindings = mutableMapOf<Dependency, Boolean>()

        survey.allNavigationCodes().forEach {
            returnBindings[Dependency(it, InCurrentNavigation)] = false
        }
        val componentCodesInCurrentNav = survey.componentsInCurrentNav(navigationIndex)
        componentCodesInCurrentNav.forEach {
            returnBindings[Dependency(it, InCurrentNavigation)] = true
        }
        returnBindings[Dependency("Survey", Validity)] = componentCodesInCurrentNav
            // only QuestionCodes
            .filter { it.startsWith("Q") && bindings[Dependency(it, Relevance)]!!.jsonPrimitive.boolean }
            .map { bindings[Dependency(it, Validity)]!!.jsonPrimitive.boolean }
            .all { it }
        // also update group validity
        survey.children.filter { it.code in componentCodesInCurrentNav }.forEach { group ->
            group.children
                .filter { it.code in componentCodesInCurrentNav && bindings[Dependency(it.code, Relevance)]!!.jsonPrimitive.boolean }
                .map { bindings[Dependency(it.code, Validity)]!!.jsonPrimitive.boolean }
                .all { it }.let { result ->
                    returnBindings[Dependency(group.code, Validity)] = result
                }
        }
        return returnBindings.mapValues { JsonPrimitive(it.value) }
    }

    fun addShowErrorsInstruction(survey: Survey, isSurveyValid: Boolean): Map<Dependency, JsonElement> {
        val returnBindings = mutableMapOf<Dependency, JsonElement>()
        returnBindings[Dependency(survey.code, ShowErrors)] = JsonPrimitive(!isSurveyValid)
        return returnBindings

    }

    fun addBeforeAfterNav(survey: Survey, navigationIndex: NavigationIndex): Map<Dependency, JsonElement> {
        val returnBindings = mutableMapOf<Dependency, JsonElement>()
        survey.navBefore(navigationIndex, bindings).let { list ->
            returnBindings[Dependency("Survey", BeforeNavigation)] = JsonArray(list.map { JsonPrimitive(it) })
            returnBindings[Dependency("Survey", HasPrevious)] =
                JsonPrimitive(list.map { bindings[Dependency(it, Relevance)]!!.jsonPrimitive.boolean }.any { it })
        }
        survey.navAfter(navigationIndex, bindings).let { list ->
            returnBindings[Dependency("Survey", AfterNavigation)] = JsonArray(list.map { JsonPrimitive(it) })
            returnBindings[Dependency("Survey", HasNext)] =
                JsonPrimitive(list.map { bindings[Dependency(it, Relevance)]!!.jsonPrimitive.boolean }.any { it })
        }
        return returnBindings
    }


}
