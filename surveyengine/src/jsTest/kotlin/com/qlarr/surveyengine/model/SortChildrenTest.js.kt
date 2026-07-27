package com.qlarr.surveyengine.model

import com.qlarr.surveyengine.dependency.componentIndices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// Exercises the `@JsExport` `sortChildren` wrapper the way a JS consumer would: a native Array of
// ComponentIndex instances in, an optional plain JS `order` object, a native Array back out.
// Mirrors commonTest ComponentIndexExt.testSortComponentIndex, but through the JS boundary function.
class SortChildrenTest {

    @Test
    fun sortsChildrenViaJsExport() {
        val indices: Array<dynamic> = componentIndices().toTypedArray()
        val order: dynamic = js(
            """({
                "Q2.order": 1,
                "Q2A2.order": 1,
                "Q2A3.order": 2,
                "Q2A1.order": 3,
                "Q1.order": 2,
                "G1.order": 2,
                "Gfood.order": 1
            })"""
        )

        val sorted = sortChildren(indices, order)
        val ordered = sorted.map { (it as ComponentIndex).code }

        assertEquals(indices.size, sorted.size)
        assertTrue(ordered.indexOf("Q2A3") < ordered.indexOf("Q1"))
        assertTrue(ordered.indexOf("Q2A3") < ordered.indexOf("Q2A1"))
        assertTrue(ordered.indexOf("Q2") < ordered.indexOf("Q1"))
        assertTrue(ordered.indexOf("Gfood") < ordered.indexOf("G1"))
    }

    @Test
    fun orderIsOptional() {
        val indices: Array<dynamic> = componentIndices().toTypedArray()
        val sorted = sortChildren(indices)
        assertEquals(indices.size, sorted.size)
    }

    @Test
    fun throwsOnNonComponentIndexElements() {
        val bogus: Array<dynamic> = arrayOf(js("({ code: \"X\" })"))
        assertFailsWith<IllegalArgumentException> {
            sortChildren(bogus)
        }
    }
}
