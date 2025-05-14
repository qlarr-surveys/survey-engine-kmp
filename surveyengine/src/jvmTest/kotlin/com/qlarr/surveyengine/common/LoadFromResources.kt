package com.qlarr.surveyengine.common


fun loadFromResources(filename:String):String{

    val resourceStream = ClassLoader.getSystemResourceAsStream(filename)
        ?: throw IllegalStateException("Could not find resource: $filename")

    return resourceStream.readAllBytes().toString(Charsets.UTF_8)
}