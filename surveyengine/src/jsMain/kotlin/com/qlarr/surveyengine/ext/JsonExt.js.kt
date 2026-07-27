package com.qlarr.surveyengine.ext

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

// JsonExt lives in commonMain and its companion returns List/Map, which are not @JsExport-able. These
// wrappers expose the same helpers to JS consumers of the npm package with JS-friendly signatures
// (Array instead of List, JSON string instead of Map).
@OptIn(ExperimentalJsExport::class)
@JsExport
fun flatObject(surveyJson: String): String = JsonExt.flatObject(surveyJson)

@OptIn(ExperimentalJsExport::class)
@JsExport
fun addChildren(surveyJson: String, code: String, state: String): String =
    JsonExt.addChildren(surveyJson, code, state)

@OptIn(ExperimentalJsExport::class)
@JsExport
fun resources(surveyJson: String): Array<String> =
    JsonExt.resources(surveyJson).toTypedArray()

// Returns the labels map serialized as a JSON object string (code -> label).
@OptIn(ExperimentalJsExport::class)
@JsExport
fun labels(surveyJson: String, parentCode: String, lang: String): String =
    JsonObject(JsonExt.labels(surveyJson, parentCode, lang).mapValues { JsonPrimitive(it.value) }).toString()
