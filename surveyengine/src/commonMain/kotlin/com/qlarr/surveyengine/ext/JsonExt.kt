package com.qlarr.surveyengine.ext

import com.qlarr.surveyengine.model.*
import kotlinx.serialization.json.*

// we have unused methods that are used by external components that import the library
@Suppress("unused")
class JsonExt {

    companion object {
        fun flatObject(surveyJson: String): String {
            val obj = jsonMapper.parseToJsonElement(surveyJson).jsonObject
            return obj.flatten().toString()
        }


        fun addChildren(surveyJson: String, code: String, state: String): String =
            jsonMapper.parseToJsonElement(surveyJson).jsonObject.addChildren(
                code,
                jsonMapper.parseToJsonElement(state).jsonObject
            ).toString()

        fun resources(surveyJson: String): List<String> =
            jsonMapper.parseToJsonElement(surveyJson).jsonObject.resources()

        fun labels(surveyJson: String, parentCode: String = "", lang: String): Map<String, String> =
            jsonMapper.parseToJsonElement(surveyJson).jsonObject.labels(parentCode, lang)
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

    return if (returnObj.containsKey("content")) {
        returnObj.reduceContent(lang, defaultLang)
    } else {
        returnObj
    }
}

private fun JsonObject.changeContent(
    path: List<String>,
    elementFrom: String,
    elementTo: String
): JsonObject  {
    val key = path.first()
    if (path.size == 1) {
        return JsonObject(toMutableMap().apply {

            val value = get(key)!!.jsonPrimitive.content

            put(key, JsonPrimitive(value.replace(elementFrom,elementTo)))
        })
    }
    return JsonObject(toMutableMap().apply {
        val value = get(key)!!.jsonObject
        put(key,value.changeContent(path.drop(1),elementFrom, elementTo))
    })
}

fun JsonObject.changeContent(
    componentPath: List<String>,
    contentPath: List<String>,
    from: String,
    to: String
): JsonObject {

    if (componentPath.isEmpty() || componentPath.size == 1 && componentPath.first() == "Survey") {
        return changeContent(contentPath, from, to)
    }

    // Get the children array name based on current node's code
    val childrenName = this["code"]!!.jsonPrimitive.content.childrenName()

    // Find the child with the next code in the path
    val childrenArray = this[childrenName] as? JsonArray
        ?: throw IllegalStateException("No children found")

    val targetChildCode = componentPath.first()
    val index = childrenArray.indexOfFirst {
        it.jsonObject["code"]!!.jsonPrimitive.content == targetChildCode
    }
    return JsonObject(toMutableMap().apply {
        put(childrenName, JsonArray(childrenArray.toMutableList().apply {
            set(
                index, childrenArray[index].jsonObject.changeContent(
                    componentPath = componentPath.drop(1),
                    contentPath = contentPath,
                    from = from,
                    to = to
                )
            )

        }))
    })
}

fun JsonObject.changeCode(
    componentPath: List<String>,
    to: String
): JsonObject {

    if (componentPath.isEmpty() || componentPath.size == 1 && componentPath.first() == "Survey") {
        return JsonObject(toMutableMap().apply {
            put("code", JsonPrimitive(to))
        })
    }

    // Get the children array name based on current node's code
    val childrenName = this["code"]!!.jsonPrimitive.content.childrenName()

    // Find the child with the next code in the path
    val childrenArray = this[childrenName] as? JsonArray
        ?: throw IllegalStateException("No children found")

    val targetChildCode = componentPath.first()
    val index = childrenArray.indexOfFirst {
        it.jsonObject["code"]!!.jsonPrimitive.content == targetChildCode
    }
    return JsonObject(toMutableMap().apply {
        put(childrenName, JsonArray(childrenArray.toMutableList().apply {
            set(
                index, childrenArray[index].jsonObject.changeCode(
                    componentPath = componentPath.drop(1),
                    to = to
                )
            )

        }))
    })
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

private fun JsonObject.addChildren(code: String, state: JsonObject): JsonObject {
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

private fun JsonObject.flatten(
    parentCode: String = "",
    returnObj: MutableMap<String, JsonElement> = mutableMapOf()
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
    returnObj[qualifiedCode] = objectWithoutChildren

    return JsonObject(returnObj)
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

internal fun SurveyComponent.copyComponentsToJson(surveyDef: JsonObject, parentCode: String = ""): JsonObject {
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
                add(surveyComponent.copyComponentsToJson(jsonChild, qualifiedCode))
            }
        }

        returnObjectMap[childrenListName] = newChildren
    }

    return JsonObject(returnObjectMap)
}

/**
 * Copies deserialization issues into the survey JSON, attaching them directly to the failed nodes.
 * For example, an issue at "G1.questions[2]" is added to the question at index 2 in group G1.
 * For instructionList, failed instructions are reconstructed from jsonFragment and injected back.
 */
internal fun JsonObject.copyDeserializationIssues(issues: List<DeserializationIssue>): JsonObject {
    if (issues.isEmpty()) return this

    val returnObjectMap = this.toMutableMap()
    val code = this["code"]?.jsonPrimitive?.content

    // Process instructionList - inject failed instructions back with issues attached
    if (code != null) {
        val instructionIssues = issues.filter { issue ->
            issue.path.matches(Regex("instructionList\\[\\d+]"))
        }

        if (instructionIssues.isNotEmpty()) {
            val currentInstructions = (this["instructionList"]?.jsonArray?.toMutableList() ?: mutableListOf())

            instructionIssues.forEach { issue ->
                // Reconstruct the failed instruction from jsonFragment
                if (issue.instructionJsonFragment.isNotBlank()) {
                    try {
                        val failedInstruction = jsonMapper.parseToJsonElement(issue.instructionJsonFragment).jsonObject
                        val instructionWithIssue = failedInstruction.toMutableMap()

                        // Attach the deserialization issue (without redundant path and fragment)
                        val simplifiedIssue = issue.simplified()
                        instructionWithIssue["deserializationIssues"] = buildJsonArray {
                            add(jsonMapper.encodeToJsonElement(simplifiedIssue))
                        }

                        currentInstructions.add(JsonObject(instructionWithIssue))
                    } catch (e: Exception) {
                        // If we can't parse the fragment, skip it
                    }
                }
            }

            returnObjectMap["instructionList"] = JsonArray(currentInstructions)
        }
    }

    // Process children (groups, questions, answers)
    if (code != null) {
        val childrenName = code.childrenName()
        if (this.containsKey(childrenName)) {
            val children = this[childrenName]?.jsonArray
            if (children != null) {
                val updatedChildren = buildJsonArray {
                    children.forEachIndexed { index, child ->
                        if (child is JsonObject) {
                            val childCode = child["code"]?.jsonPrimitive?.content

                            // Find issues that target this specific child by index
                            val directIssues = issues.filter { issue ->
                                // Match issues like "G1.questions[2]" where index is 2
                                issue.path.matches(Regex(".*\\b$childrenName\\[$index\\]"))
                            }

                            // Find issues that belong to descendants of this child
                            val descendantIssues = if (childCode != null) {
                                issues.filter { issue ->
                                    issue.path.startsWith("$childCode.")
                                }.map { issue ->
                                    // Remove the child code prefix from the path
                                    issue.copy(path = issue.path.removePrefix("$childCode."))
                                }
                            } else {
                                emptyList()
                            }

                            var updatedChild = child

                            // Add direct issues to this child node
                            if (directIssues.isNotEmpty()) {
                                val childMap = updatedChild.toMutableMap()
                                val existingIssues = (updatedChild["deserializationIssues"] as? JsonArray)?.toMutableList() ?: mutableListOf()
                                directIssues.forEach { issue ->
                                    // Strip out redundant path and jsonFragment since issue is colocated with the node
                                    val simplifiedIssue = issue.simplified()
                                    existingIssues.add(jsonMapper.encodeToJsonElement(simplifiedIssue))
                                }
                                childMap["deserializationIssues"] = JsonArray(existingIssues)
                                updatedChild = JsonObject(childMap)
                            }

                            // Recursively process descendants
                            if (descendantIssues.isNotEmpty()) {
                                updatedChild = updatedChild.copyDeserializationIssues(descendantIssues)
                            }

                            add(updatedChild)
                        } else {
                            add(child)
                        }
                    }
                }
                returnObjectMap[childrenName] = updatedChildren
            }
        }
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