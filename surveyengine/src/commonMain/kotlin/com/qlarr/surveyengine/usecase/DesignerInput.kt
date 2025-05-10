package com.qlarr.surveyengine.usecase

import kotlinx.serialization.json.JsonObject
import com.qlarr.surveyengine.model.ComponentIndex

data class DesignerInput(
    val state: JsonObject,
    val componentIndexList: List<ComponentIndex>
)