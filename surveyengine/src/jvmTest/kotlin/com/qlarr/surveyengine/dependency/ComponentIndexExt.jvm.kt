package com.qlarr.surveyengine.dependency

import com.qlarr.surveyengine.model.ComponentIndex
import com.qlarr.surveyengine.model.jsonMapper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.serializer


@OptIn(ExperimentalSerializationApi::class)
actual fun componentIndices(): List<ComponentIndex> {
    // Load from the resources folder in the classpath
    val resourcePath = "component_indices.json"

    val resourceStream = ClassLoader.getSystemResourceAsStream(resourcePath)
        ?: throw IllegalStateException("Could not find resource: $resourcePath")

    return jsonMapper.decodeFromStream(ListSerializer(serializer<ComponentIndex>()), resourceStream)
}