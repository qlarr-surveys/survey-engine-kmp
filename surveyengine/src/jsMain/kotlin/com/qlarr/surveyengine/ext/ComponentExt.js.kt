package com.qlarr.surveyengine.ext

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

// The commonMain helpers below are String extension functions, which Kotlin/JS `@JsExport` cannot
// export. These top-level wrappers expose them to JS consumers of the npm package with JS-friendly
// signatures (plain params, Array instead of List).

@OptIn(ExperimentalJsExport::class)
@JsExport
fun splitToComponentCodes(code: String): Array<String> =
    code.splitToComponentCodes().toTypedArray()

@OptIn(ExperimentalJsExport::class)
@JsExport
fun isGroupCode(code: String): Boolean = code.isGroupCode()

@OptIn(ExperimentalJsExport::class)
@JsExport
fun isQuestionCode(code: String): Boolean = code.isQuestionCode()

@OptIn(ExperimentalJsExport::class)
@JsExport
fun isAnswerCode(code: String): Boolean = code.isAnswerCode()