package com.qlarr.surveyengine.scriptengine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray


actual fun getValidate(): ScriptEngineValidate {

    return object : ScriptEngineValidate {
        override fun validate(input: String): String {
            val items = Json.parseToJsonElement(input).jsonArray
            console.log(items)
            val result = validateCode(items.toString())
            console.log(result)
            return result
        }
    }
}


actual fun getNavigate(): ScriptEngineNavigate {
    TODO("Not yet implemented")
}