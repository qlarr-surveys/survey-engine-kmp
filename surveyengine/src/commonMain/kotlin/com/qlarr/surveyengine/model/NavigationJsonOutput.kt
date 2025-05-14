package com.qlarr.surveyengine.model

import com.qlarr.surveyengine.model.exposed.NavigationIndex
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

@Serializable
internal data class NavigationJsonOutput(
    val survey: JsonObject = buildJsonObject {},
    val state: JsonObject = buildJsonObject {},
    val navigationIndex: NavigationIndex,
    val toSave: Map<String, @Contextual Any> = mapOf()
)