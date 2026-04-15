package com.qlarr.surveyengine.model

import kotlinx.serialization.Serializable

@Serializable
enum class ComponentError {
    RESERVED_CODE,
    DUPLICATE_CODE,
    EMPTY_PARENT,
    MISPLACED_END_GROUP,
    NO_END_GROUP
}