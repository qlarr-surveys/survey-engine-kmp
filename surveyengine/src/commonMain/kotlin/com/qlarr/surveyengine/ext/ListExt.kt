package com.qlarr.surveyengine.ext


fun <T> List<T>.getDuplicates(): Set<T> {
    return this.groupingBy { it }
        .eachCount()
        .filter { it.value > 1 }
        .keys
}