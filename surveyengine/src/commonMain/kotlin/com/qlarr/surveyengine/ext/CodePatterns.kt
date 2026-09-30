package com.qlarr.surveyengine.ext


const val VALID_COMPONENT_CODE_PATTERN = "([SGQA])[a-z_0-9]+"
const val VALID_FORMAT_PREFIX = "format_"
const val VALID_FORMAT_INSTRUCTION_PATTERN = "format_[a-z0-9_]+\$"
const val VALID_SURVEY_CODE = "^Survey$"
const val VALID_GROUP_CODE = "^G[a-z0-9_]+$"
const val VALID_QUESTION_CODE = "^Q[a-z0-9_]+$"
const val VALID_ANSWER_CODE = ".*A[a-z0-9_]+$"
const val VALID_SINGLE_ANSWER_CODE = "^A[a-z0-9_]+$"
const val VALID_REPEAT_TOKEN = "^[a-z0-9_]+$"

private val COMPONENT_CODE_REFERENCE = Regex("\\b[SGQA][a-z0-9_]*")

fun String.remapComponentCodes(remap: (String) -> String): String =
    COMPONENT_CODE_REFERENCE.replace(this) { remap(it.value) }