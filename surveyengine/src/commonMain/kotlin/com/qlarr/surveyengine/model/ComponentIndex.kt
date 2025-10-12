package com.qlarr.surveyengine.model

import kotlinx.serialization.Serializable

@Serializable
data class ComponentIndex(
    val code: String,
    val parent: String?,
    val children: List<String> = listOf(),
    val minIndex: Int,
    val maxIndex: Int,
    val prioritisedSiblings: Set<String> = setOf(),
    val dependencies: Set<ReservedCode> = setOf()
)

fun MutableList<ComponentIndex>.sortChildren(
    order: Map<String, Any>
): MutableList<ComponentIndex> {
    val component = first()
    val children = component.children
    if (children.isEmpty()){
        return this
    }
    val sortedChildren = children.sortedBy {
        order["$it.order"] as? Int ?: (children.indexOf(it) + 1)
    }
    return mutableListOf<ComponentIndex>().apply {
        add(component)
        sortedChildren.map {  child ->
            val isLast = children.indexOf(child) == children.size - 1
            addAll(
                this@sortChildren.subList(
                    fromIndex = this@sortChildren.indexOfFirst { it.code == child },
                    toIndex = if (isLast) {
                        this@sortChildren.size

                    } else {
                        this@sortChildren.indexOfFirst { item ->
                            children.contains(item.code) && children.indexOf(item.code) > children.indexOf(child)
                        }
                    }
                ).sortChildren(order)
            )
        }
    }
}