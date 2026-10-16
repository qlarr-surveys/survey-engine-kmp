package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.context.execute.sanitize
import com.qlarr.surveyengine.context.nestedComponents
import com.qlarr.surveyengine.ext.VALID_CALCULATION_CODE
import com.qlarr.surveyengine.model.Dependency
import com.qlarr.surveyengine.model.Instruction
import com.qlarr.surveyengine.model.ReservedCode
import com.qlarr.surveyengine.model.Survey
import com.qlarr.surveyengine.model.jsonMapper
import com.qlarr.surveyengine.scriptengine.ScriptEngineCalculate
import com.qlarr.surveyengine.scriptengine.ScriptEngineValidate
import com.qlarr.surveyengine.scriptengine.ValidationScriptError
import kotlinx.serialization.json.*

private val calculationVariableCodes = setOf(
    ReservedCode.Value,
    ReservedCode.ValueMeta,
    ReservedCode.Mode,
    ReservedCode.Disqualified
)

internal class CalculationUseCase(
    private val validationEngine: ScriptEngineValidate,
    survey: Survey
) {
    private val allowedVariables: List<String> = survey.sanitize().nestedComponents().flatMap { component ->
        component.instructionList
            .filterIsInstance<Instruction.State>()
            .filter { it.reservedCode in calculationVariableCodes }
            .map { Dependency(component.code, it.reservedCode).toValueKey() }
    }.distinct()

    private val componentCodes: List<String> = allowedVariables.map { it.substringBefore('.') }.distinct()

    fun validate(calculations: List<Calculation>): List<Calculation> {
        val scriptErrors = validateScripts(calculations)
        val duplicateCodes = calculations.groupingBy { it.code }.eachCount().filterValues { it > 1 }.keys
        val codeRegex = Regex(VALID_CALCULATION_CODE)
        return calculations.mapIndexed { index, calculation ->
            calculation.copy(errors = buildList {
                if (!calculation.code.matches(codeRegex)) add(CalculationError.InvalidCode)
                if (calculation.code in duplicateCodes) add(CalculationError.DuplicateCode)
                addAll(scriptErrors[index].map { CalculationError.ScriptError(it.message, it.start, it.end) })
            })
        }
    }

    fun calculate(
        scriptEngine: ScriptEngineCalculate,
        calculations: List<Calculation>,
        responses: Map<String, JsonObject>
    ): Map<String, CalculationOutput> {
        val runnable = calculations.filter { it.errors.isEmpty() }
        return responses.mapValues { (_, values) ->
            if (runnable.isEmpty()) {
                CalculationOutput()
            } else {
                try {
                    jsonMapper.decodeFromString(
                        CalculationOutput.serializer(),
                        scriptEngine.calculate(calculationScript(runnable, values))
                    )
                } catch (e: Exception) {
                    CalculationOutput(errors = runnable.associate {
                        it.code to CalculationRuntimeError("EngineFailure", e.message ?: "")
                    })
                }
            }
        }
    }

    private fun validateScripts(calculations: List<Calculation>): List<List<ValidationScriptError>> {
        if (calculations.isEmpty()) {
            return listOf()
        }
        val items = buildJsonArray {
            calculations.forEach { calculation ->
                addJsonObject {
                    put("script", calculation.expression)
                    putJsonArray("allowedVariables") {
                        allowedVariables.forEach { add(it) }
                    }
                }
            }
        }
        return jsonMapper.decodeFromString(validationEngine.validate(items.toString()))
    }

    private fun calculationScript(calculations: List<Calculation>, values: JsonObject): String {
        return buildJsonObject {
            putJsonArray("codes") {
                componentCodes.forEach { add(it) }
            }
            put("values", JsonObject(values.filterKeys { it in allowedVariables }))
            putJsonArray("calculations") {
                calculations.forEach { calculation ->
                    addJsonObject {
                        put("code", calculation.code)
                        put("expression", calculation.expression)
                    }
                }
            }
        }.toString()
    }
}
