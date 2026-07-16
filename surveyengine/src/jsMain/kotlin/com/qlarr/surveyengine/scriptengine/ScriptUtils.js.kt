package com.qlarr.surveyengine.scriptengine

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
private fun decode(b64: String): String = Base64.decode(b64).decodeToString()

actual fun commonScript(): CommonScriptProvider {
    return object : CommonScriptProvider {
        override val script: String
            get() = decode(ScriptResourcesGenerated.COMMON_SCRIPT_B64)
    }
}

actual fun engineScript(): EngineScriptProvider {
    return object : EngineScriptProvider {
        override val script: String
            get() = decode(ScriptResourcesGenerated.COMMON_SCRIPT_B64) + "\n" +
                    decode(ScriptResourcesGenerated.INITIAL_SCRIPT_B64)
    }
}
