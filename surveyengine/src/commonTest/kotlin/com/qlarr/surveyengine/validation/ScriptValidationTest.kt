package com.qlarr.surveyengine.validation

import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.scriptengine.ValidationScriptError
import com.qlarr.surveyengine.scriptengine.getValidate
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals

class ScriptValidationTest {

    private fun validate(script: String): List<ValidationScriptError> {
        val input = buildJsonArray {
            addJsonObject {
                put("script", script)
                putJsonArray("allowedVariables") { add("Q1.value") }
            }
        }
        return jsonMapper.decodeFromString<List<List<ValidationScriptError>>>(
            getValidate().validate(input.toString())
        ).single()
    }

    private fun assertRejected(message: String, vararg scripts: String) {
        scripts.forEach { script ->
            assertEquals(listOf(message), validate(script).map { it.message }.distinct(), script)
        }
    }

    @Test
    fun functions_are_only_allowed_as_callbacks() = assertRejected(
        "Functions are only allowed as callbacks",
        "(x) => x",
        "(function(x){ return x; })",
        "[(x) => x]",
        "[function(x){ return x; }]",
        "({ 'map': (x) => x }).map(1)",
        "[1].map((a) => ({ a(){ return 1; } }))",
        "[].concat((x) => x)",
        "[].push((x) => x)",
        "[1].map(() => (x) => x)",
        "[1].map(() => { return (x) => x; })",
        "[1].find((x) => x, (y) => y)",
        "[1].reduce((a, b) => a, (x) => x)",
        "'a'.replace((x) => x, 'b')",
        "Math.max((x) => x)",
        "Object.keys((x) => x)",
        "QlarrScripts.isVoid((x) => x)",
        "new Date((x) => x)",
    )

    @Test
    fun async_callbacks_are_rejected() = assertRejected(
        "Async functions are not allowed",
        "[1].map(async (x) => x)",
        "[1].map(async (x) => { return x; })",
        "[1].map(async function(x){ return x; })",
    )

    @Test
    fun generator_callbacks_are_rejected() = assertRejected(
        "Generator functions are not allowed",
        "[1].map(function*(x){ return x; })",
    )

    @Test
    fun named_function_callbacks_are_rejected() = assertRejected(
        "Named functions are not allowed",
        "[1].map(function process(x){ return x; })",
    )

    @Test
    fun callback_params_must_be_simple_identifiers() = assertRejected(
        "Only simple identifier parameters are allowed",
        "[1].map((a, b, c, d = process.env) => 0)",
        "['x'].map((a, b, c, d = (Q1.value = process.env.X)) => Q1.value)",
    )

    @Test
    fun callback_params_may_not_shadow_reserved_names() {
        assertRejected("Date is a reserved name", "[1].map((Date) => new Date())")
        assertRejected("RegExp is a reserved name", "[1].map((RegExp) => new RegExp('a'))")
        assertRejected(
            "Math is a reserved name",
            "[1].map((Math) => Math.abs())",
            "[1].map((x, Math) => x)",
            "[1].map((x) => [2].map((Math) => x))",
        )
        assertRejected("Number is a reserved name", "[1].map((Number) => Number.isNaN())")
        assertRejected("Object is a reserved name", "[1].map((Object) => Object.keys())")
        assertRejected("QlarrScripts is a reserved name", "[1].map((QlarrScripts) => QlarrScripts.isVoid())")
        assertRejected("undefined is a reserved name", "[1].map((undefined) => undefined)")
        assertRejected("Q1 is a reserved name", "[1].map((Q1) => Q1.value)")
    }

    @Test
    fun callbacks_may_not_nest_past_the_limit() = assertRejected(
        "Callbacks may not be nested more than 3 levels deep",
        "[1].map((a) => [1].map((b) => [1].map((c) => [1].map((d) => d))))",
        "[1].map((a) => a.map((b) => b.map((c) => c.map((d) => d))))",
        "[1].map((a) => [1].map((b) => [1].map((c) => [1].filter((d) => d))))",
    )

    @Test
    fun computed_member_calls_are_rejected() = assertRejected(
        "Computed member expressions are not allowed",
        "['constructor'].map((abs) => Math[abs]('x'))",
        "['constructor'].map((keys) => Object[keys]('x'))",
        "['constructor'].map((isVoid) => QlarrScripts[isVoid]('x'))",
        "['constructor'].map((map) => [1][map]('x'))",
        "['constructor'].map((map) => [1]?.[map]('x'))",
    )

    @Test
    fun computed_keys_are_rejected() = assertRejected(
        "Computed keys are not allowed",
        "({ ['a']: 1 })",
        "({ ['__proto__']: 1 })",
    )

    @Test
    fun proto_keys_are_rejected() = assertRejected(
        "__proto__ is not allowed as a key",
        "({ '__proto__': [1] })",
        "[1].map((__proto__) => ({ __proto__: 1 }))",
    )

    @Test
    fun in_and_instanceof_operators_are_rejected() {
        assertRejected("in operator is not allowed", "'x' in {}")
        assertRejected("instanceof operator is not allowed", "[1].map((a) => a instanceof a)")
    }

    @Test
    fun bigint_literals_are_rejected() = assertRejected(
        "BigInt literals are not allowed",
        "1n",
        "[1n]",
    )

    @Test
    fun directives_are_rejected() = assertRejected(
        "directives are not allowed",
        "'use strict'",
        "[1].map((x) => { 'use strict'; return x; })",
    )

    @Test
    fun string_padding_methods_are_rejected() {
        assertRejected("Unidentified instance method: padEnd", "'a'.padEnd(1000000000)")
        assertRejected("Unidentified instance method: padStart", "'a'.padStart(1000000000)")
    }

    @Test
    fun callbacks_in_their_declared_position_are_accepted() {
        listOf(
            "[1].map((x) => x * 2)",
            "[1].map((x) => { return x * 2; })",
            "[1].map(function(x){ return x * 2; })",
            "[1, 2].filter(function(x){ return x; })",
            "'a'.replace(/a/, function(m){ return m; })",
            "[1, 2].filter((x, i) => i > 0)",
            "[1, 2].reduce((acc, x) => acc + x, 0)",
            "[2, 1].sort((a, b) => a - b)",
            "'a'.replace(/a/, (m) => m)",
            "[1].map((a) => [1].map((b) => [1].map((c) => a + b + c)))",
        ).forEach { script ->
            assertEquals(emptyList(), validate(script), script)
        }
    }
}
