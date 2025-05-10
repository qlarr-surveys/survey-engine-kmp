package com.qlarr.surveyengine.model

import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule

// This replaces the former Jackson mapper with kotlinx.serialization
val jsonMapper = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
    serializersModule = SerializersModule {
        contextual(Any::class, AnySerializer)
    }

}