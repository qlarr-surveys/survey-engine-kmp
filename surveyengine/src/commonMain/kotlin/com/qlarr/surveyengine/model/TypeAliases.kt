package com.qlarr.surveyengine.model

typealias ImpactMap = Map<Dependency, List<Dependent>>
typealias StringImpactMap = Map<String, List<String>>
typealias DependencyMapBundle = Pair<ImpactMap, DependencyMap>
typealias DependencyMap = Map<Dependent, List<Dependency>>
typealias MutableImpactMap = MutableMap<Dependency, List<Dependent>>
typealias MutableDependencyMap = MutableMap<Dependent, List<Dependency>>


fun ImpactMap.toStringImpactMap(): StringImpactMap {
    val returnMap = mutableMapOf<String, List<String>>()
    keys.forEach { dependency ->
        returnMap[dependency.toValueKey()] = get(dependency)!!.map { it.toValueKey() }
    }
    return returnMap
}

fun StringImpactMap.toImpactMap(): ImpactMap {
    val returnMap = mutableMapOf<Dependency, List<Dependent>>()
    keys.forEach { key ->
        key.toDependency()?.let { dependency ->
            returnMap[dependency] = get(key)!!.map { it.toDependent() }
        }
    }
    return returnMap
}

fun Map<String, Any>.withDependencyKeys(): Map<Dependency, Any> = mapKeys { it.key.toDependency()!! }
fun Map<Dependency, Any>.withStringKeys(): Map<String, Any> = mapKeys { it.key.toValueKey() }