package com.qlarr.surveyengine.ext


fun String.splitToComponentCodes(): List<String> {
    val regex = Regex(VALID_COMPONENT_CODE_PATTERN)
    return regex.findAll(this).map { it.value }.toList()
}

fun String.isGroupCode(): Boolean {
    return this.matches(Regex(VALID_GROUP_CODE))
}

fun String.isAnswerCode(): Boolean {
    return this.matches(Regex(VALID_SINGLE_ANSWER_CODE))
}
fun String.isQuestionCode(): Boolean {
    return this.matches(Regex(VALID_QUESTION_CODE))
}

fun String.isSurveyCode(): Boolean {
    return this == "Survey"
}
fun String.isUniqueCode() = this.isQuestionCode()
            || this.isGroupCode()
            || this.isSurveyCode()

fun String.childrenName() = when{
    this.isQuestionCode() -> "answers"
    this.isGroupCode() -> "questions"
    this.isSurveyCode() -> "groups"
    this.isAnswerCode() -> "answers"
    else -> throw IllegalStateException("illegal code ")
}


