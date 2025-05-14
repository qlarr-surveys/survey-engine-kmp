package com.qlarr.surveyengine.ext

import com.qlarr.surveyengine.model.*
import kotlinx.serialization.json.*


class JsonExt {

    companion object {
        fun flatObject(surveyJson: String): String =
            jsonMapper.encodeToJsonElement(surveyJson).jsonObject.flatten().toString()

        fun addChildren(surveyJson: String, code: String, state: String): String =
            jsonMapper.encodeToJsonElement(surveyJson).jsonObject.addChildren(
                code,
                jsonMapper.encodeToJsonElement(state).jsonObject
            ).toString()

        fun resources(surveyJson: String): List<String> =
            jsonMapper.encodeToJsonElement(surveyJson).jsonObject.resources()

        fun labels(surveyJson: String, parentCode: String = "", lang: String): Map<String, String> =
            jsonMapper.encodeToJsonElement(surveyJson).jsonObject.labels(parentCode, lang)
    }
}


internal fun JsonObject.copyToJSON(
    inCurrentNavigation: Boolean,
    lang: String? = null,
    defaultLang: String
): JsonObject {
    val returnObj = buildJsonObject {
        if (!inCurrentNavigation) {
            listOf("type", "code", "qualifiedCode", "groupType").forEach { key ->
                this@copyToJSON[key]?.let {
                    put(key, it)
                }
            }
        } else {
            this@copyToJSON.keys.forEach {
                if (it !in listOf(
                        "answers",
                        "groups",
                        "questions",
                        "instructionList",
                        "errors",
                        "content"
                    )
                ) {
                    put(it, this@copyToJSON[it]!!)
                }
            }
        }
        put("inCurrentNavigation", JsonPrimitive(inCurrentNavigation))

        if (this@copyToJSON.containsKey("content")) {
            put("content", this@copyToJSON["content"]!!)
        }
    }

    if (returnObj.containsKey("content")) {
        returnObj.reduceContent(lang, defaultLang)
    }

    return returnObj
}

internal fun JsonObject.copyReducedToJSON(
    sortedSurveyComponent: SurveyComponent,
    reducedSurveyComponent: SurveyComponent?,
    lang: String? = null,
    defaultLang: String
): JsonObject {
    val returnObj = copyToJSON(reducedSurveyComponent != null, lang, defaultLang)
    if (reducedSurveyComponent == null && sortedSurveyComponent.elementType == SurveyElementType.QUESTION) {
        return returnObj
    }

    val returnChildren = buildJsonArray {
        val children = listOf("answers", "groups", "questions")
            .mapNotNull { childKey ->
                this@copyReducedToJSON[childKey]?.jsonArray
            }

        if (children.size > 1) {
            throw IllegalStateException("More than once Child!!!")
        } else if (children.isEmpty()) {
            return returnObj
        }

        val jsonChildren = children[0]

        sortedSurveyComponent.children.forEach { orderedComponent ->
            val childComponent =
                reducedSurveyComponent?.children?.firstOrNull { it.code == orderedComponent.code }
            val childNode = jsonChildren.first {
                it.jsonObject["code"]?.jsonPrimitive?.content == orderedComponent.code
            }.jsonObject

            add(
                childNode.copyReducedToJSON(
                    orderedComponent,
                    childComponent,
                    lang,
                    defaultLang
                )
            )
        }
    }

    if (returnChildren.size > 0) {
        return JsonObject(returnObj + (this["code"]!!.jsonPrimitive.content.childrenName() to returnChildren))
    }

    return returnObj
}

fun JsonObject.addChildren(code: String, state: JsonObject): JsonObject {
    val returnObj = buildJsonObject {
        this@addChildren["children"]?.jsonArray?.let { childrenNodes ->
            val children = buildJsonArray {
                childrenNodes.forEach { child ->
                    val childQualifiedCode = child.jsonObject["qualifiedCode"]!!.jsonPrimitive.content
                    val childCode = child.jsonObject["code"]!!.jsonPrimitive.content
                    val childObj = state[childQualifiedCode]!!.jsonObject
                    add(childObj.addChildren(childCode, state))
                }
            }
            put(code.childrenName(), children)
        }

        put("code", JsonPrimitive(code))

        this@addChildren.keys.forEach { fieldName ->
            if (fieldName != "children") {
                put(fieldName, this@addChildren[fieldName]!!)
            }
        }
    }

    return returnObj
}

internal fun JsonObject.flatten(
    parentCode: String = "",
    returnObj: JsonObject = buildJsonObject {}
): JsonObject {
    val code = this["code"]!!.jsonPrimitive.content
    val qualifiedCode = if (code.isUniqueCode()) code else parentCode + code

    val children = listOf("answers", "groups", "questions")
        .mapNotNull { childKey ->
            this[childKey]?.jsonArray
        }

    if (children.size > 1) {
        throw IllegalStateException("More than once Child!!!")
    }

    val childrenNames = buildJsonArray {
        children.firstOrNull()?.forEach { child ->
            val childObj = child.jsonObject
            val childCode = childObj["code"]!!.jsonPrimitive.content
            val childQualifiedCode =
                if (childCode.isUniqueCode()) childCode else qualifiedCode + childCode

            val childName = buildJsonObject {
                put("code", childObj["code"]!!)

                if (childObj.containsKey("type")) {
                    put("type", childObj["type"]!!)
                }

                if (childObj.containsKey("groupType")) {
                    put("groupType", childObj["groupType"]!!)
                }

                put("qualifiedCode", JsonPrimitive(childQualifiedCode))
            }

            add(childName)
            childObj.flatten(qualifiedCode, returnObj)
        }
    }

    val objectWithoutChildren = buildJsonObject {
        if (childrenNames.size > 0) {
            put("children", childrenNames)
        }

        this@flatten.keys.forEach {
            if (it !in listOf("answers", "groups", "questions", "code", "qualifiedCode")) {
                put(it, this@flatten[it]!!)
            }
        }
    }

    return JsonObject(returnObj + (qualifiedCode to objectWithoutChildren))
}


// only public for testing
internal fun JsonObject.reduceContent(lang: String? = null, defaultLang: String): JsonObject {
    val updatedObj = this.toMutableMap()

    (this["content"] as? JsonObject)?.let { content ->
        val mergedNode = content[defaultLang] as? JsonObject ?: buildJsonObject {}
        val mergedMap = mergedNode.toMutableMap()

        lang?.let {
            (content[lang] as? JsonObject)?.let { localisedNode ->
                localisedNode.entries.forEach { (key, value) ->
                    mergedMap[key] = value
                }
            }
        }

        updatedObj["content"] = JsonObject(mergedMap)
    }

    (this["validation"] as? JsonObject)?.let { validation ->
        val validationMap = validation.toMutableMap()

        validation.entries.forEach { (validationField, fieldValue) ->
            (fieldValue as? JsonObject)?.let { validationItem ->
                val validationItemMap = validationItem.toMutableMap()

                validationItem["content"]?.let { contentToBeLocalised ->
                    if (contentToBeLocalised is JsonObject && contentToBeLocalised.isNotEmpty()) {
                        val node = lang?.let { contentToBeLocalised[it] }
                            ?: contentToBeLocalised[defaultLang]

                        if (node != null) {
                            validationItemMap["content"] = node
                            validationMap[validationField] = JsonObject(validationItemMap)
                        }
                    }
                }
            }
        }

        updatedObj["validation"] = JsonObject(validationMap)
    }

    return JsonObject(updatedObj)
}

internal fun JsonObject.resources(): List<String> {
    val returnList = mutableListOf<String>()

    (this["resources"] as? JsonObject)?.let { resources ->
        resources.entries.forEach { (_, value) ->
            if (value is JsonPrimitive && value.isString) {
                val strValue = value.content
                if (strValue.isNotBlank()) {
                    returnList.add(strValue)
                }
            }
        }
    }

    val code = this["code"]!!.jsonPrimitive.content
    (this[code.childrenName()] as? JsonArray)?.let { childrenNodes ->
        childrenNodes.forEach { child ->
            returnList.addAll(child.jsonObject.resources())
        }
    }

    return returnList
}

internal fun JsonObject.labels(parentCode: String = "", lang: String): Map<String, String> {
    val returnMap = mutableMapOf<String, String>()
    val code = this["code"]!!.jsonPrimitive.content
    val qualifiedCode = if (code.isUniqueCode()) code else parentCode + code
    val label = getLabel(lang, lang)
    returnMap[qualifiedCode] = label

    (this[code.childrenName()] as? JsonArray)?.let { childrenNodes ->
        childrenNodes.forEach { child ->
            returnMap.putAll(child.jsonObject.labels(qualifiedCode, lang))
        }
    }

    return returnMap
}

private fun JsonArray.getByCode(code: String): JsonObject {
    for (i in 0 until size) {
        val obj = this[i].jsonObject
        if (obj.containsKey("code") && obj["code"]?.jsonPrimitive?.content == code) {
            return obj
        }
    }
    throw IllegalStateException("Child with corresponding code not found")
}

internal fun SurveyComponent.copyErrorsToJSON(surveyDef: JsonObject, parentCode: String = ""): JsonObject {
    if (!surveyDef.containsKey("code") || code != surveyDef["code"]?.jsonPrimitive?.content) {
        throw IllegalStateException("copyErrorsToJSON: copying into a JsonObject with different code: $code")
    }

    val qualifiedCode = uniqueCode(parentCode)
    val returnObjectMap = surveyDef.toMutableMap()

    returnObjectMap["qualifiedCode"] = JsonPrimitive(qualifiedCode)

    if (instructionList.isNotEmpty()) {
        returnObjectMap["instructionList"] = jsonMapper.encodeToJsonElement(instructionList)
    } else {
        returnObjectMap.remove("instructionList")
    }

    if (errors.isNotEmpty()) {
        returnObjectMap["errors"] = jsonMapper.encodeToJsonElement(errors)
    } else {
        returnObjectMap.remove("errors")
    }

    val childType = elementType.childType()

    if (children.isNotEmpty()) {
        val childrenListName = childType.nameAsChildList()
        val jsonChildren = surveyDef[childrenListName]?.jsonArray ?: buildJsonArray {}

        val newChildren = buildJsonArray {
            children.filter { it.elementType == childType }.forEachIndexed { index, surveyComponent ->
                val jsonChild = jsonChildren.getOrNull(index)?.jsonObject ?: buildJsonObject {}
                add(surveyComponent.copyErrorsToJSON(jsonChild, qualifiedCode))
            }
        }

        returnObjectMap[childrenListName] = newChildren
    }

    return JsonObject(returnObjectMap)
}

internal fun JsonObject.getLabel(lang: String, defaultLang: String): String {
    return (this["content"] as? JsonObject)?.let { content ->
        val langContent = content[lang] as? JsonObject
        val defaultLangContent = content[defaultLang] as? JsonObject

        langContent?.get("label")?.jsonPrimitive?.contentOrNull
            ?: defaultLangContent?.get("label")?.jsonPrimitive?.contentOrNull
    } ?: ""
}

internal fun JsonObject.getChild(codes: List<String>): JsonObject {
    if (codes.isEmpty())
        return this

    val childrenName = this["code"]!!.jsonPrimitive.content.childrenName()
    val child = (this[childrenName] as? JsonArray)?.first { jsonNode ->
        jsonNode.jsonObject["code"]?.jsonPrimitive?.content == codes.first()
    }?.jsonObject ?: throw IllegalStateException("Child not found")

    return child.getChild(codes.drop(1))
}

internal fun SurveyComponent.getLabels(
    componentJson: JsonObject,
    parentCode: String,
    lang: String,
    defaultLang: String,
    impactMap: ImpactMap
): Map<Dependency, String> {
    val returnMap = mutableMapOf<Dependency, String>()

    if (!componentJson.containsKey("code") || code != componentJson["code"]?.jsonPrimitive?.content) {
        throw IllegalStateException("getLabels: copying into a JsonObject with different code: $code")
    }

    val qualifiedCode = uniqueCode(parentCode)
    if (impactMap.keys.contains(Dependency(qualifiedCode, ReservedCode.Label))) {
        returnMap[Dependency(qualifiedCode, ReservedCode.Label)] =
            componentJson.getLabel(lang, defaultLang)
    }

    if (children.isNotEmpty()) {
        val childType = elementType.childType()
        val jsonChildren = componentJson[childType.nameAsChildList()]?.jsonArray ?: buildJsonArray {}

        children.forEach { child ->
            val jsonObject = jsonChildren.getByCode(child.code)
            returnMap.putAll(
                child.getLabels(
                    jsonObject,
                    qualifiedCode,
                    lang,
                    defaultLang,
                    impactMap
                )
            )
        }
    }

    return returnMap
}

fun jsonObjectToMap(jsonObject: JsonObject): Map<String, Any> {
    val map: MutableMap<String, Any> = HashMap()
    jsonObject.entries.forEach { (key, value) ->
        map[key] = jsonValueToObject(value)
    }
    return map
}

fun jsonArrayToList(jsonArray: JsonArray): List<Any> {
    val list: MutableList<Any> = ArrayList()
    for (item in jsonArray) {
        list.add(jsonValueToObject(item))
    }
    return list
}

fun jsonPrimitiveToAny(jsonPrimitive: JsonPrimitive): Any {
    return when {
        jsonPrimitive.isString -> jsonPrimitive.content
        jsonPrimitive.booleanOrNull != null -> jsonPrimitive.boolean
        jsonPrimitive.intOrNull != null -> jsonPrimitive.int
        jsonPrimitive.longOrNull != null -> jsonPrimitive.long
        jsonPrimitive.doubleOrNull != null -> jsonPrimitive.double
        else -> jsonPrimitive.content
    }
}

internal fun jsonValueToObject(jsonValue: JsonElement): Any {
    return when (jsonValue) {
        is JsonObject -> {
            jsonObjectToMap(jsonValue)
        }

        is JsonArray -> {
            jsonArrayToList(jsonValue)
        }

        is JsonPrimitive -> {
            jsonPrimitiveToAny(jsonValue)
        }

        else -> {
            jsonValue.toString()
        }
    }
}

internal fun valueToJson(value: Any): JsonElement {
    return when (value) {
        is Map<*, *> -> {
            mapToJsonObject(value)
        }

        is List<*> -> {
            listToJsonArray(value)
        }

        is String -> {
            JsonPrimitive(value)
        }

        is Number -> {
            JsonPrimitive(value)
        }

        is Boolean -> {
            JsonPrimitive(value)
        }

        else -> {
            JsonPrimitive(value.toString())
        }
    }
}

internal fun mapToJsonObject(map: Map<*, *>): JsonObject {
    val entries = map.entries.associate {
        it.key.toString() to valueToJson(it.value!!)
    }
    return JsonObject(entries)
}

internal fun listToJsonArray(list: List<*>): JsonArray {
    return buildJsonArray {
        list.forEach {
            if (it != null) {
                add(valueToJson(it))
            }
        }
    }
}