package com.qlarr.surveyengine.dependency

import com.qlarr.surveyengine.model.ComponentIndex
import com.qlarr.surveyengine.model.jsonMapper
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import kotlin.js.Json


@JsModule("./component_indices.json")
@JsNonModule
external val testData: Json

actual fun componentIndices(): List<ComponentIndex> {
    try {


        // Convert the dynamic JS object to a string to decode with kotlinx.serialization
        val jsonString = JSON.stringify(testData)

        // Use kotlinx.serialization to decode the JSON string
        return jsonMapper.decodeFromString(ListSerializer(serializer<ComponentIndex>()), jsonString)
    } catch (e: dynamic) {
        // Handle JS exceptions which come as dynamic objects
        console.error("Failed to load component indices", e)
        throw IllegalStateException("Could not load resource:  Error: ${e.message ?: e}")
    }
}