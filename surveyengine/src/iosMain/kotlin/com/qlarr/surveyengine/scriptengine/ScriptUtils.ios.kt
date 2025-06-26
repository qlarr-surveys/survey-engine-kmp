package com.qlarr.surveyengine.scriptengine

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

@OptIn(ExperimentalForeignApi::class)
actual fun commonScript(): CommonScriptProvider {
    return object : CommonScriptProvider {
        override val script: String
            get() {
                val bundle = NSBundle.mainBundle
                val path = bundle.pathForResource("common_script", "js", "scripts")
                    ?: throw IllegalStateException("Could not find common_script.js in bundle")
                return NSString.stringWithContentsOfFile(
                    path = path,
                    encoding = NSUTF8StringEncoding,
                    error = null
                ) ?: throw IllegalStateException("Could not read common_script.js")
            }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun engineScript(): EngineScriptProvider {
    return object : EngineScriptProvider {
        override val script: String
            get() {
                val bundle = NSBundle.mainBundle
                val commonPath = bundle.pathForResource("common_script", "js","scripts")
                    ?: throw IllegalStateException("Could not find common_script.js in bundle")
                val commonScript = NSString.stringWithContentsOfFile(
                    path = commonPath,
                    encoding = NSUTF8StringEncoding,
                    error = null
                ) ?: throw IllegalStateException("Could not read common_script.js")

                val initialPath = bundle.pathForResource("initial_script", "js","scripts")
                    ?: throw IllegalStateException("Could not find initial_script.js in bundle")
                val initialScript = NSString.stringWithContentsOfFile(
                    path = initialPath,
                    encoding = NSUTF8StringEncoding,
                    error = null
                ) ?: throw IllegalStateException("Could not read initial_script.js")

                return commonScript + "\n" + initialScript
            }
    }
}