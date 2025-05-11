package com.qlarr.surveyengine.ext


interface CommonScriptProvider {
    val script: String
}

interface EngineScriptProvider {
    val script: String
}

expect fun commonScript(): CommonScriptProvider
expect fun engineScript(): EngineScriptProvider

