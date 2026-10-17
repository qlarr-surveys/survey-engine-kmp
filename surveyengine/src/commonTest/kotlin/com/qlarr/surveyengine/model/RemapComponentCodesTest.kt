package com.qlarr.surveyengine.model

import com.qlarr.surveyengine.ext.remapComponentCodes
import com.qlarr.surveyengine.model.Instruction.Format
import com.qlarr.surveyengine.model.Instruction.ParentRelevance
import com.qlarr.surveyengine.model.Instruction.PriorityGroup
import com.qlarr.surveyengine.model.Instruction.PriorityGroups
import com.qlarr.surveyengine.model.Instruction.RandomGroups
import com.qlarr.surveyengine.model.Instruction.SimpleState
import com.qlarr.surveyengine.model.Instruction.SkipInstruction
import com.qlarr.surveyengine.model.exposed.ReturnType
import kotlin.test.Test
import kotlin.test.assertEquals

class RemapComponentCodesTest {

    private fun suffix(vararg codes: String): (String) -> String {
        val set = codes.toSet()
        return { code -> if (code in set) "${code}_a" else code }
    }

    @Test
    fun text_helper_remaps_whole_codes_only() {
        val remap = suffix("Q1", "Q2")
        assertEquals("Q1_a.value == true", "Q1.value == true".remapComponentCodes(remap))
        assertEquals("Q1_a.value + Q2_a.value", "Q1.value + Q2.value".remapComponentCodes(remap))
        assertEquals("Q1_aA1.value", "Q1A1.value".remapComponentCodes(remap))
        assertEquals("Q10.value", "Q10.value".remapComponentCodes(remap))
    }

    @Test
    fun text_helper_leaves_external_codes_untouched() {
        assertEquals(
            "Qbrands.value.includes(Q1_a.value)",
            "Qbrands.value.includes(Q1.value)".remapComponentCodes(suffix("Q1"))
        )
    }

    @Test
    fun simple_state_remaps_text_and_return_type() {
        val relevance = SimpleState("Q1.value", ReservedCode.ConditionalRelevance)
        assertEquals("Q1_a.value", (relevance.remapComponentCodes(suffix("Q1")) as SimpleState).text)

        val value = SimpleState("", ReservedCode.Value, returnType = ReturnType.Enum(setOf("A1", "A2")))
        val remapped = value.remapComponentCodes(suffix("A1")) as SimpleState
        assertEquals(ReturnType.Enum(setOf("A1_a", "A2")), remapped.returnType)
    }

    @Test
    fun format_remaps_text() {
        val format = Format(code = "format_label", text = "Q1.label", lang = "en")
        assertEquals("Q1_a.label", (format.remapComponentCodes(suffix("Q1")) as Format).text)
    }

    @Test
    fun random_groups_remaps_codes() {
        val random = RandomGroups(groups = listOf(listOf("Q1", "Q2"), listOf("Q3")))
        val remapped = random.remapComponentCodes(suffix("Q1", "Q2")) as RandomGroups
        assertEquals(listOf("Q1_a", "Q2_a"), remapped.groups[0].codes)
        assertEquals(listOf("Q3"), remapped.groups[1].codes)
    }

    @Test
    fun priority_groups_remaps_child_codes() {
        val priority = PriorityGroups(priorities = listOf(PriorityGroup(listOf("Q1", "Q2"))))
        val remapped = priority.remapComponentCodes(suffix("Q1", "Q2")) as PriorityGroups
        assertEquals(listOf("Q1_a", "Q2_a"), remapped.priorities[0].weights.map { it.code })
    }

    @Test
    fun parent_relevance_remaps_children() {
        val parent = ParentRelevance(children = listOf(listOf("A1", "A2"), listOf("A3")))
        val remapped = parent.remapComponentCodes(suffix("A1", "A3")) as ParentRelevance
        assertEquals(listOf(listOf("A1_a", "A2"), listOf("A3_a")), remapped.children)
    }

    @Test
    fun skip_instruction_remaps_target_code_and_text() {
        val skip = SkipInstruction(skipToComponent = "G5", text = "Q1.value")
        val remapped = skip.remapComponentCodes(suffix("G5", "Q1"))
        assertEquals("Q1_a.value", remapped.text)
    }
}
