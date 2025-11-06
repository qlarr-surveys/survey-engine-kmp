package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.ext.changeCode
import com.qlarr.surveyengine.ext.changeContent
import com.qlarr.surveyengine.ext.copyComponentsToJson
import com.qlarr.surveyengine.ext.splitToComponentCodes
import com.qlarr.surveyengine.model.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

internal class ChangeCodeUseCaseWrapperImpl(private val surveyJson: String) : ChangeCodeUseCaseWrapper {


    override fun changeCode(from: String, to: String): String {
        val jsonOutput: ValidationJsonOutput =
            jsonMapper.decodeFromString(ValidationJsonOutput.serializer(), surveyJson)
        val newValidationJsonOutput = jsonOutput.changeCode(from, to)
        return ValidationUseCaseWrapper.create(
            newValidationJsonOutput.survey.toString()
        ).validate()
    }
}

internal fun ValidationJsonOutput.changeCode(from: String, to: String): ValidationJsonOutput {
    var surveyComponent = jsonMapper.decodeFromJsonElement(serializer<Survey>(), survey)
    val impactMap = impactMap.toImpactMap()
    val componentIndex = componentIndexList
    val keys = impactMap
        .keys.filter { it.componentCode.contains(from) }

    val pathToComponentParent = componentIndex.parents(from)
    val pathToComponent = pathToComponentParent + from.splitToComponentCodes().last()
    surveyComponent = surveyComponent.changeRandomInstruction(
        path = pathToComponentParent,
        from = from.splitToComponentCodes().last(),
        to = to.splitToComponentCodes().last(),
    ) as Survey

    var surveyJson = survey

    componentIndex.forEach { componentIndexItem ->
        componentIndexItem.dependencies
            .filterIsInstance<ReservedCode.Skip>()
            .forEach { reservedCode ->
                val impactedElementCode = componentIndexItem.code
                val path =
                    componentIndex.parents(impactedElementCode) + impactedElementCode.splitToComponentCodes().last()
                surveyComponent = surveyComponent.changeSkipInstruction(
                    path = path,
                    instructionCode = reservedCode,
                    from = from,
                    to = to,
                ) as Survey
            }

    }
    keys.forEach { key ->
        impactMap[key]?.forEach { dependency ->
            val impactedElementCode = dependency.componentCode
            val path = componentIndex.parents(impactedElementCode) + impactedElementCode.splitToComponentCodes().last()
            val instructionCode = dependency.instructionCode
            surveyComponent = surveyComponent.changeInstruction(
                fullPath = path,
                surveyJson = surveyJson,
                onSurveyJsonModified = { surveyJson = it },
                path = path,
                instructionCode = instructionCode,
                from = from,
                to = to,
            ) as Survey
        }
    }


    return copy(survey = surveyComponent.copyComponentsToJson(surveyJson).changeCode(pathToComponent, to))
}

private fun SurveyComponent.changeSkipInstruction(
    path: List<String>,
    instructionCode: ReservedCode.Skip,
    from: String,
    to: String
): SurveyComponent = if (path.isEmpty()) {
    val modifiedInstructionList = instructionList.map { instruction ->
        if (instruction is Instruction.SkipInstruction && instruction.reservedCode == instructionCode) {
            instruction.copy(skipToComponent = instruction.skipToComponent.replace(from, to))
        } else {
            instruction
        }
    }
    duplicate(instructionList = modifiedInstructionList)
} else {
    val newChildren = children.map { child ->
        if (child.code == path.first()) {
            child.changeSkipInstruction(
                path = path.drop(1),
                instructionCode = instructionCode,
                from = from,
                to = to
            )
        } else {
            child
        }
    }

    duplicate(
        children = newChildren
    )
}

private fun SurveyComponent.changeInstruction(
    fullPath: List<String>,
    surveyJson: JsonObject,
    onSurveyJsonModified: (JsonObject) -> Unit,
    path: List<String>,
    instructionCode: String,
    from: String,
    to: String,
): SurveyComponent = if (path.isEmpty() || (path.size == 1 && path.first() == "Survey")) {
    val modifiedInstructionList = instructionList.map { instruction ->
        if (instruction.code == instructionCode) {
            when (instruction) {
                is Instruction.Reference -> {
                    onSurveyJsonModified(surveyJson.changeContent(fullPath, instruction.contentPath, from, to))
                    val newReferences = instruction.references.map { reference ->
                        reference.replace(from, to)
                    }
                    instruction.copy(references = newReferences)

                }

                is Instruction.State -> {
                    instruction.withValidatedText(instruction.text.replace(from, to))

                }

                else -> {
                    throw IllegalStateException("")
                }
            }

        } else {
            instruction
        }
    }
    duplicate(instructionList = modifiedInstructionList)
} else {
    val newChildren = children.map { child ->
        if (child.code == path.first()) {
            child.changeInstruction(
                fullPath = fullPath,
                surveyJson = surveyJson,
                onSurveyJsonModified = onSurveyJsonModified,
                path = path.drop(1),
                instructionCode = instructionCode,
                from = from,
                to = to
            )
        } else {
            child
        }
    }

    duplicate(
        children = newChildren
    )
}


private fun SurveyComponent.changeRandomInstruction(
    path: List<String>,
    from: String,
    to: String,
): SurveyComponent {
    return if (path.isEmpty()) {
        val modifiedInstructionList = instructionList.map { instruction ->
            if (instruction is Instruction.RandomGroups) {
                instruction.copy(
                    groups = instruction.groups.map { randomGroup ->
                        randomGroup.copy(
                            codes = randomGroup.codes.map {
                                if (it == from) to else it
                            }
                        )
                    }
                )
            } else {
                instruction
            }
        }
        duplicate(instructionList = modifiedInstructionList)
    } else {
        val newChildren = children.map { child ->
            if (child.code == path.first()) {
                child.changeRandomInstruction(path.drop(1), from, to)
            } else {
                child
            }
        }

        duplicate(
            children = newChildren
        )
    }

}