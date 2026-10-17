package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.model.NavigationJsonOutput
import com.qlarr.surveyengine.model.exposed.*
import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.scriptengine.getNavigate
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals

class RandomizedSkipTest {
    private fun q(code: String, skipText: String?) = buildJsonObject {
        put("code", code); put("type", "text")
        put("instructionList", buildJsonArray {
            addJsonObject { put("code", "value"); put("text", ""); put("returnType", "string"); put("isActive", false) }
            if (skipText != null) addJsonObject {
                put("code", "skip_to_G4"); put("skipToComponent", "G4"); put("toEnd", false); put("disqualify", true)
                put("text", skipText); put("returnType", "boolean"); put("isActive", true)
            }
        })
    }
    private fun g(code: String, type: String, vararg qs: JsonObject) = buildJsonObject {
        put("code", code); put("groupType", type); put("questions", JsonArray(qs.toList()))
    }
    @Test
    fun skips_in_both_randomized_groups_do_not_form_a_cycle() {
        val design = buildJsonObject {
            put("code", "Survey")
            put("defaultLang", buildJsonObject { put("code", "en"); put("name", "English") })
            put("navigationMode", "group_by_group")
            put("instructionList", buildJsonArray {
                addJsonObject { put("code", "random_group"); put("groups", buildJsonArray { addJsonObject { put("codes", buildJsonArray { add("G2"); add("G3") }) } }) }
            })
            put("groups", buildJsonArray {
                add(g("G1", "GROUP", q("Q1", null)))
                add(g("G2", "GROUP", q("Q2", "Q2.value == \"a\"")))
                add(g("G3", "GROUP", q("Q3", "Q3.value == \"b\"")))
                add(g("G4", "END"))
            })
        }
        val processed = ValidationUseCaseWrapper.create(design.toString()).validate()
        val output = NavigationUseCaseWrapper.init(
            values = """{"G2.order":1,"G3.order":2,"Q2.value":"x"}""", processedSurvey = processed,
            navigationMode = NavigationMode.GROUP_BY_GROUP, navigationIndex = NavigationIndex.Group("G2"),
            navigationDirection = NavigationDirection.Next, skipInvalid = true, surveyMode = SurveyMode.ONLINE
        ).navigate(getNavigate())
        val navigation = jsonMapper.decodeFromString(NavigationJsonOutput.serializer(), output)
        assertEquals(NavigationIndex.Group("G3"), navigation.navigationIndex)
    }
}
