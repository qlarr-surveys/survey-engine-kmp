package com.qlarr.surveyengine.dependency

import com.qlarr.surveyengine.model.ComponentIndex
import com.qlarr.surveyengine.model.jsonMapper
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

@OptIn(ExperimentalForeignApi::class)
actual fun componentIndices(): List<ComponentIndex> {

    val bundle = NSBundle.mainBundle
    val path = bundle.pathForResource("component_indices", "json", "test-resources")
        ?: throw IllegalStateException("Could not find component_indices.js in bundle")
    val string =  NSString.stringWithContentsOfFile(
        path = path,
        encoding = NSUTF8StringEncoding,
        error = null
    ) ?: throw IllegalStateException("Could not read common_script.js")

    return jsonMapper.decodeFromString(ListSerializer(serializer<ComponentIndex>()), string)

}