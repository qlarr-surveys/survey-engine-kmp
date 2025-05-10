package com.qlarr.surveyengine.model

import kotlinx.serialization.Serializable

@Serializable
data class ComponentIndex(
    val code: String,
    val parent: String?,
    val children: List<String> = listOf(),
    val minIndex: Int,
    val maxIndex: Int,
    val prioritisedSiblings: Set<String> = setOf(),
    val dependencies: Set<ReservedCode> = setOf()
)