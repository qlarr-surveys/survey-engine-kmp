package com.qlarr.surveyengine.scriptengine



actual fun commonScript(): CommonScriptProvider {
    return object : CommonScriptProvider {
        override val script: String
            get() = ""

    }
}

actual fun engineScript(): EngineScriptProvider {
    return object : EngineScriptProvider {
        override val script: String
            get() = ""

    }
}