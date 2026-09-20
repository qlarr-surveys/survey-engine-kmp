package com.qlarr.surveyengine.dependency

import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.Instruction.SimpleState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepetitionComponentIndexTest {

    private fun repeatable() = RepeatInfo.Repeatable(range = listOf("a"), relevanceInstruction = "Qx.includes('{{token}}')")

    private fun value() = listOf(SimpleState("", ReservedCode.Value))

    private fun List<ComponentIndex>.depsOf(code: String) =
        accessibleDependencies(code).map { it.componentCode }

    @Test
    fun marks_repetition_scope_on_repeatable_and_repeated_subtrees() {
        val survey = Survey(
            groups = listOf(
                Group("G1", repeatInfo = repeatable(), questions = listOf(Question("Q1"))),
                Group("G2", repeatInfo = RepeatInfo.Repeated("a"), questions = listOf(Question("Q2"))),
                Group("G3", questions = listOf(Question("Q3")))
            )
        )

        val indices = listOf(survey).componentIndices()
        fun scopeOf(code: String) = indices.first { it.code == code }.repetitionScope

        assertNull(scopeOf("Survey"))
        assertEquals("G1", scopeOf("G1"))
        assertEquals("G1", scopeOf("Q1"))
        assertEquals("G2", scopeOf("G2"))
        assertEquals("G2", scopeOf("Q2"))
        assertNull(scopeOf("G3"))
        assertNull(scopeOf("Q3"))
    }

    @Test
    fun include_repeatables_false_drops_repeatable_subtree_but_keeps_repeated() {
        val survey = Survey(
            groups = listOf(
                Group("G1", repeatInfo = repeatable(), questions = listOf(Question("Q1"))),
                Group("G2", repeatInfo = RepeatInfo.Repeated("a"), questions = listOf(Question("Q2"))),
                Group("G3", questions = listOf(Question("Q3")))
            )
        )

        val codes = listOf(survey).componentIndices(includeRepeatables = false).map { it.code }

        assertEquals(listOf("Survey", "G2", "Q2", "G3", "Q3"), codes)
    }

    @Test
    fun external_component_cannot_reference_into_a_repeatable() {
        val survey = Survey(
            groups = listOf(
                Group(
                    "G1", repeatInfo = repeatable(),
                    questions = listOf(
                        Question("Q1", instructionList = value()),
                        Question("Q2", instructionList = value())
                    )
                ),
                Group("G2", questions = listOf(Question("Q3", instructionList = value())))
            )
        )

        val indices = listOf(survey).componentIndices()

        assertTrue("Q1" in indices.depsOf("Q2"))       // internal, same scope
        assertFalse("Q1" in indices.depsOf("Q3"))      // external cannot reach in
        assertFalse("Q2" in indices.depsOf("Q3"))
    }

    @Test
    fun a_copy_cannot_reference_another_copy() {
        val survey = Survey(
            groups = listOf(
                Group(
                    "G1_a", repeatInfo = RepeatInfo.Repeated("a"),
                    questions = listOf(
                        Question("Q1_a", instructionList = value()),
                        Question("Q2_a", instructionList = value())
                    )
                ),
                Group(
                    "G1_b", repeatInfo = RepeatInfo.Repeated("b"),
                    questions = listOf(
                        Question("Q1_b", instructionList = value()),
                        Question("Q2_b", instructionList = value())
                    )
                )
            )
        )

        val q2bDeps = listOf(survey).componentIndices().depsOf("Q2_b")

        assertTrue("Q1_b" in q2bDeps)       // same copy
        assertFalse("Q1_a" in q2bDeps)      // cross-copy blocked
        assertFalse("Q2_a" in q2bDeps)
    }

    @Test
    fun skip_destinations_exclude_repeatables_but_include_repeated() {
        val survey = Survey(
            groups = listOf(
                Group("G0", questions = listOf(Question("Q0", instructionList = value()))),
                Group("G1", repeatInfo = repeatable(), questions = listOf(Question("Q1", instructionList = value()))),
                Group(
                    "G2", repeatInfo = RepeatInfo.Repeated("a"),
                    questions = listOf(Question("Q2", instructionList = value()))
                ),
                Group("G3", questions = listOf(Question("Q3", instructionList = value())))
            )
        )

        val destinations = listOf(survey).componentIndices(includeRepeatables = false).jumpDestinations("Q0")

        assertFalse("G1" in destinations)   // cannot skip into a repeatable
        assertFalse("Q1" in destinations)   // ...nor its descendants
        assertTrue("G2" in destinations)    // can skip into a repeated instance
        assertTrue("G3" in destinations)
    }

    @Test
    fun excluding_a_repeatable_child_does_not_dangle_its_parents_children() {
        val survey = Survey(
            groups = listOf(
                Group(
                    "G1",
                    questions = listOf(
                        Question("Q1", repeatInfo = repeatable(), instructionList = value()),
                        Question("Q2", instructionList = value())
                    )
                ),
                Group("G2", questions = listOf(Question("Q3", instructionList = value())))
            )
        )

        val q3deps = listOf(survey).componentIndices(includeRepeatables = false).depsOf("Q3")

        assertFalse("Q1" in q3deps)   // repeatable excluded
        assertTrue("Q2" in q3deps)    // normal sibling still reachable, no crash
    }
}
