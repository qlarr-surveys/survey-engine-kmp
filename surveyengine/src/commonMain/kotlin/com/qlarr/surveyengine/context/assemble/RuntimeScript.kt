package com.qlarr.surveyengine.context.assemble

import com.qlarr.surveyengine.model.ChildlessComponent
import com.qlarr.surveyengine.model.DependencyMap
import com.qlarr.surveyengine.model.Dependent
import com.qlarr.surveyengine.model.Instruction
import com.qlarr.surveyengine.model.Instruction.State

fun List<ChildlessComponent>.runtimeScript(dependencyMap: DependencyMap): String {
    val componentFunctions = mapNotNull { it.componentRuntimeScript(dependencyMap) }
    val stringToInject = if (componentFunctions.isEmpty()) {
        ""
    } else {
        componentFunctions.joinToString(separator = ",\n", postfix = ",\n")
    }
    return TEMPLATE.replace("%s", stringToInject)

}

fun ChildlessComponent.componentRuntimeScript(dependencyMap: DependencyMap): String? {
    val parts = instructionList.mapNotNull {
        when (it) {
            is State -> {
                it.stateRuntimeScript(code, dependencyMap)
            }

            is Instruction.Reference -> {
                it.formatRuntimeScript(code, dependencyMap)
            }

            else -> {
                null
            }
        }
    }
    return if (parts.isEmpty()) {
        null
    } else {
        parts.joinToString(separator = ",\n", prefix = "\t$code : {\n", postfix = "\n\t}")
    }


}

fun State.stateRuntimeScript(componentCode: String, dependencyMap: DependencyMap): String? {
    return if (!isActive || !reservedCode.isRuntime) {
        null
    } else {
        var finalText = text
        dependencyMap[Dependent(componentCode, code)]?.forEach {
            val dependencyCode = Regex("(?<![\\w\\d])${it.asCode()}(?![\\w\\d])")
            val newDependencyCode = "state.${it.asCode()}"
            finalText = finalText.replace(dependencyCode, newDependencyCode)
        }
        "\t\t$code: function(state) {return $finalText;}"
    }
}

fun Instruction.Reference.formatRuntimeScript(componentCode: String, dependencyMap: DependencyMap): String {
    var finalText = text()
    dependencyMap[Dependent(componentCode, code)]?.forEach {
        val dependencyCode = "${it.componentCode}.${it.reservedCode.code}"
        val newDependencyCode = "state.$dependencyCode"
        finalText = finalText.replace(dependencyCode, newDependencyCode)
    }
    return "\t\t$code: function(state) {return $finalText;}"
}

private const val TEMPLATE = "qlarrRuntime = {\n" +
        "%s\n" +
        "}"