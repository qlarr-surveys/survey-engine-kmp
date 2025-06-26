package com.qlarr.surveyengine.scriptengine


actual fun commonScript(): CommonScriptProvider {
    return object : CommonScriptProvider {
        override val script: String
            get() = javaClass.classLoader.getResourceAsStream("scripts/common_script.js")!!.reader().readText()
    }
}

actual fun engineScript(): EngineScriptProvider {
    return object : EngineScriptProvider {
        override val script: String
            get() {
                val classLoader = javaClass.classLoader
                val commonScript = classLoader.getResourceAsStream("scripts/common_script.js")!!.reader().readText()
                val initialScript = classLoader.getResourceAsStream("scripts/initial_script.js")!!.reader().readText()
                return commonScript + "\n" + initialScript
            }

    }
}