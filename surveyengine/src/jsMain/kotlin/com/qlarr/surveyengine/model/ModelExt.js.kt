package com.qlarr.surveyengine.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

// The model helpers below are extension/member functions on Kotlin types (List<ComponentIndex>,
// ComponentIndex, StringImpactMap) that Kotlin/JS `@JsExport` cannot export directly. These
// top-level wrappers expose them to JS consumers of the npm package using JSON strings for the
// complex types and JS-friendly return types (Array/Boolean/String).

// `List<ComponentIndex>.parents(code)` — pass the component-index list as a JSON array string.
@OptIn(ExperimentalJsExport::class)
@JsExport
fun parents(componentIndexListJson: String, code: String): Array<String> =
    jsonMapper.decodeFromString<List<ComponentIndex>>(componentIndexListJson)
        .parents(code)
        .toTypedArray()

// `ComponentIndex.hasSkip()` — pass a single ComponentIndex as a JSON object string.
@OptIn(ExperimentalJsExport::class)
@JsExport
fun hasSkip(componentIndexJson: String): Boolean =
    jsonMapper.decodeFromString<ComponentIndex>(componentIndexJson).hasSkip()

// `StringImpactMap.toImpactMap()` — takes a StringImpactMap (`{ "code.reserved": ["code.instr"] }`)
// as a JSON string and returns the normalized map as a JSON string. The intermediate ImpactMap keys
// are `Dependency`/`Dependent` objects, which are not JS-representable, so we round-trip back to the
// string-keyed form (dropping entries whose key is not a valid dependency) for the JS boundary.
@OptIn(ExperimentalJsExport::class)
@JsExport
fun toImpactMap(stringImpactMapJson: String): String {
    val stringImpactMap: StringImpactMap = jsonMapper.decodeFromString(stringImpactMapJson)
    return jsonMapper.encodeToString(stringImpactMap.toImpactMap().toStringImpactMap())
}

// ReservedCode is a sealed class that is not `@JsExport`-able. JS consumers generally only need the
// string codes, so we expose them via a singleton. Extend this as more codes are needed on the JS side.
@OptIn(ExperimentalJsExport::class)
@JsExport
object ReservedCodes {
    val conditionalRelevance: String = ReservedCode.ConditionalRelevance.code
}