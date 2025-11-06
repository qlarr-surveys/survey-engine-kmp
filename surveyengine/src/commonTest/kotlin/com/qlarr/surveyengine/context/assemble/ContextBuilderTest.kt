package com.qlarr.surveyengine.context.assemble

import com.qlarr.surveyengine.context.nestedComponents
import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.Instruction.*
import com.qlarr.surveyengine.model.exposed.ReturnType
import com.qlarr.surveyengine.scriptengine.getValidate
import com.qlarr.surveyengine.usecase.wrapToSurvey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("LocalVariableName")
class ContextBuilderTest {

    @Test
    fun bindComponentUnit_will_attach_scrip_failure_errors_to_instructions_with_messed_scripts() {

        val SURVEY_TITLE_INVALID_SCRIPT = Survey(
            listOf(
                SimpleState("toksdfen.firstName++ > 'Wawa'", ReservedCode.Value, isActive = true),
            ),
            groups = listOf(
                Group("G1", questions = listOf(Question("Q1"))),
                Group(
                    "G2", questions = listOf(Question("Q2")), instructionList = listOf(
                        Reference("reference_en_desc", references = listOf("G1.kabaka"), lang = SurveyLang.EN.code)
                    )
                )
            )
        )
        val contextManager = ContextBuilder(mutableListOf(SURVEY_TITLE_INVALID_SCRIPT), getValidate())
        contextManager.validate()
        assertTrue(contextManager.components[0].instructionList[0].errors[0] is InstructionError.ScriptError)
        assertEquals(
            InstructionError.InvalidReference("G1.kabaka", false),
            contextManager.components[0].children[1].instructionList[0].errors[0]
        )

    }

    @Test
    fun bindComponentUnit_will_add_validation_to_match_enum() {

        val SURVEY_TITLE_INVALID_SCRIPT = Survey(
            groups = listOf(
                Group(
                    "G1", questions = listOf(
                        Question(
                            code = "Q1",
                            instructionList = listOf(
                                SimpleState(
                                    text = "",
                                    reservedCode = ReservedCode.Value,
                                    returnType = ReturnType.Enum(setOf("A1", "A2", "A3"))
                                )
                            )
                        )
                    )
                ),
            )
        )
        val contextManager = ContextBuilder(mutableListOf(SURVEY_TITLE_INVALID_SCRIPT), getValidate())
        contextManager.validate()
        val validationList = contextManager.components[0].children[0].children[0].instructionList
            .filterIsInstance<SimpleState>()
            .first {
                it.reservedCode is ReservedCode.ValidationRule
            }
        assertEquals(
            expected = "QlarrScripts.isNotVoid(Q1.value) && [\"A1\",\"A2\",\"A3\"].indexOf(Q1.value) == -1",
            actual = validationList.text
        )
        assertEquals(
            expected = "validation_enum",
            actual = validationList.code
        )
    }

    @Test
    fun bindComponentUnit_will_add_validation_to_match_a_list() {

        val SURVEY_TITLE_INVALID_SCRIPT = Survey(
            groups = listOf(
                Group(
                    "G1", questions = listOf(
                        Question(
                            code = "Q1",
                            instructionList = listOf(
                                SimpleState(
                                    text = "",
                                    reservedCode = ReservedCode.Value,
                                    returnType = ReturnType.List(setOf("A1", "A2", "A3"))
                                )
                            )
                        )
                    )
                ),
            )
        )
        val contextManager = ContextBuilder(mutableListOf(SURVEY_TITLE_INVALID_SCRIPT), getValidate())
        contextManager.validate()
        val validationList = contextManager.components[0].children[0].children[0].instructionList
            .filterIsInstance<SimpleState>()
            .first {
                it.reservedCode is ReservedCode.ValidationRule
            }
        assertEquals(
            expected = "QlarrScripts.isNotVoid(Q1.value) && !Q1.value.every(element => [\"A1\",\"A2\",\"A3\"].includes(element))",
            actual = validationList.text
        )
        assertEquals(
            expected = "validation_list",
            actual = validationList.code
        )
    }


    @Test
    fun a_component_with_fatal_error_cannot_be_referenced() {

        val SURVEY_TITLE_INVALID_SCRIPT = Survey(
            listOf(
                SimpleState("toksdfen.firstName++ > 'Wawa'", ReservedCode.Value, isActive = true),
            ),
            groups = listOf(
                Group("G1"),
                Group(
                    "G2", questions = listOf(Question("Q1")), instructionList = listOf(
                        Reference("reference_en_desc", references = listOf("G1.kabaka"), lang = SurveyLang.EN.code)
                    )
                )
            )
        )
        val contextManager = ContextBuilder(mutableListOf(SURVEY_TITLE_INVALID_SCRIPT), getValidate())
        contextManager.validate()
        assertTrue(contextManager.components[0].instructionList[0].errors[0] is InstructionError.ScriptError)
        assertEquals(
            InstructionError.InvalidReference("G1.kabaka", true),
            contextManager.components[0].children[1].instructionList[0].errors[0]
        )

    }

    @Test
    fun bindComponentUnit_will_attach_scrip_failure_errors_to_instructions_with_messed_scripts2() {

        val SURVEY_TITLE_INVALID_SCRIPT = Survey(
            listOf(
                SimpleState(";;toksdfen.firstName++ > 'Wawa'", ReservedCode.Value, isActive = true),
                Reference(
                    "reference_1",
                    listOf(
                        "Q1Afirst_name.validity",
                        "Q1Alast_name.validity",
                        "Q1Alast_name.value",
                        "Q1Afirst_name.value"
                    ),
                    emptyList(),
                    SurveyLang.EN.code
                )
            ), groups = listOf(Group("G1"))
        )
        val contextManager = ContextBuilder(mutableListOf(SURVEY_TITLE_INVALID_SCRIPT), getValidate())
        contextManager.validate()
        assertTrue(contextManager.components[0].instructionList[0].errors[0] is InstructionError.ScriptError)
        assertEquals(
            InstructionError.InvalidReference("Q1Afirst_name.validity", true),
            contextManager.components[0].instructionList[1].errors[0]
        )
        assertEquals(
            InstructionError.InvalidReference("Q1Alast_name.validity", true),
            contextManager.components[0].instructionList[1].errors[1]
        )
        assertEquals(
            InstructionError.InvalidReference("Q1Alast_name.value", true),
            contextManager.components[0].instructionList[1].errors[2]
        )
        assertEquals(
            InstructionError.InvalidReference("Q1Afirst_name.value", true),
            contextManager.components[0].instructionList[1].errors[3]
        )

    }

    @Test
    fun skip_equations_are_validated() {
        val QUESTION_ONE = Question("Q1")
        val QUESTION_TWO = Question(
            "Q2", listOf(
                SkipInstruction(
                    skipToComponent = "Q4",
                    code = "skip_to_question_4",
                    isActive = false,
                    condition = "toksdfen.firstName++ > 'Wawa'"
                )
            )
        )
        val QUESTION_THREE = Question(
            "Q3", listOf(
                SkipInstruction(
                    skipToComponent = "Q7",
                    code = "skip_to_question_7",
                    isActive = false,
                    condition = "true"
                )
            )
        )
        val QUESTION_FOUR = Question(
            "Q4", listOf(
                SkipInstruction(
                    skipToComponent = "G2",
                    code = "skip_to_G2",
                    toEnd = true,
                    isActive = false,
                    condition = "true"
                )
            )
        )
        val Survey = Survey(
            groups = listOf(
                Group("G1", questions = listOf(QUESTION_ONE, QUESTION_TWO, QUESTION_THREE, QUESTION_FOUR)),
                Group("G2", groupType = GroupType.END, questions = listOf(Question("Q10")))
            )
        )
        val contextManager = ContextBuilder(mutableListOf(Survey), getValidate())
        contextManager.validate()

        assertEquals(
            InstructionError.InvalidSkipReference("Q7"),
            contextManager.components[0].children[0].children[2].instructionList[0].errors[0]
        )
        assertEquals(
            InstructionError.SkipToEndOfEndGroup,
            contextManager.components[0].children[0].children[3].instructionList[0].errors[0]
        )
        contextManager.components
    }

    @Test
    fun duplicate_instructions_are_skipped() {
        val QUESTION = Question(
            "Q1", listOf(
                SimpleState("false", ReservedCode.ConditionalRelevance),
                SimpleState("2", ReservedCode.ConditionalRelevance)
            )
        )
        val contextManager = ContextBuilder(mutableListOf(QUESTION), getValidate())
        contextManager.validate()
    }

    @Test
    fun constructs_disqualify_instruction() {
        val Survey = Survey(
            groups = listOf(
                Group(
                    code = "G1",
                    questions = listOf(
                        Question(
                            code = "Q1",
                            instructionList = listOf(
                                SimpleState(text = "", ReservedCode.Value)
                            )

                        ),
                        Question(
                            "Q2",
                            instructionList = listOf(
                                SkipInstruction(
                                    text = "Q1.value > 3",
                                    skipToComponent = "Gend",
                                    disqualify = true
                                )
                            )
                        ),
                        Question(
                            "Q3",
                            instructionList = listOf(
                                SkipInstruction(
                                    text = "Q1.value <8 ",
                                    skipToComponent = "Gend",
                                    disqualify = true
                                )
                            )
                        ),
                    )
                ),
                Group(
                    code = "G2",
                    questions = listOf(
                        Question(
                            code = "Q4",
                            instructionList = listOf(
                                SkipInstruction(
                                    text = "Q1.value != 0 ",
                                    skipToComponent = "Gend",
                                    toEnd = true,
                                    disqualify = true
                                )
                            )
                        ),
                        Question(
                            code = "Q5",
                            instructionList = listOf(
                                SkipInstruction(
                                    text = "eval(window)",
                                    skipToComponent = "Gend",
                                    disqualify = true
                                )
                            )
                        ),
                        Question("Q6"),
                    )
                ),
                Group(
                    "Gend",
                    questions = listOf(Question("Qend")),
                    groupType = GroupType.END
                ),
            )
        )
        val contextManager = ContextBuilder(mutableListOf(Survey), getValidate())
        contextManager.validate()
        assertEquals("Q1.value > 3 && Q1.value <8 ", (contextManager.components[0].instructionList.firstOrNull {
            it is SimpleState && it.reservedCode == ReservedCode.Disqualified
        }!! as SimpleState).text)
    }


    @Test
    fun fwd_dependency_errors_are_flagged() {
        val G1Q1 = Question("Q1", listOf(SimpleState("Q2.value", ReservedCode.Value, isActive = true)))
        val G1Q2 = Question("Q2", listOf(SimpleState("Q1.value", ReservedCode.Value, isActive = true)))
        val G1 = Group("G1", listOf(SimpleState("", ReservedCode.Value)), listOf(G1Q1, G1Q2))
        val contextManager = ContextBuilder(mutableListOf(G1.wrapToSurvey()), getValidate())
        contextManager.validate()

        assertTrue(contextManager.components[0].children[0].children[0].instructionList[0].errors[0] is InstructionError.ForwardDependency)
        assertTrue(contextManager.components[0].children[0].children[1].instructionList[0].errors.isEmpty())
    }

    @Test
    fun dependency_error_analyzer_analyzes_circular_relevance_to_own_parent_dependency() {

        val A1 = Answer("A1", listOf(SimpleState("G1.relevance", ReservedCode.ConditionalRelevance)))
        val Q1 = Question(
            "Q1",
            answers = listOf(A1),
            instructionList = listOf(ParentRelevance(children = listOf(listOf("A1"))))
        )
        val G1 = Group("G1", questions = listOf(Q1))
        val contextManager = ContextBuilder(mutableListOf(G1.wrapToSurvey()), getValidate())
        contextManager.validate()
        assertTrue(contextManager.components[0].children[0].children[0].children[0].instructionList[0].errors[0] is InstructionError.ForwardDependency)

    }

    @Test
    fun validates_skip_references() {

        val Survey = Survey(
            instructionList = listOf(RandomGroups(groups = listOf(listOf("G2", "G3")))),
            groups = listOf(
                Group(
                    "G1",
                    instructionList = listOf(
                        RandomGroups(groups = listOf(listOf("Q1", "Q2"), listOf("Q3", "Q4"))),
                        SkipInstruction(code = "skip_to_G3_from_G1", skipToComponent = "G3")
                    ),
                    questions = listOf(
                        Question(
                            "Q1",
                            instructionList = listOf(
                                SkipInstruction(
                                    code = "skip_to_Q8_from_Q1",
                                    skipToComponent = "Q8"
                                )
                            )
                        ),
                        Question("Q2"),
                        Question("Q3"),
                        Question("Q4")
                    ),
                ),
                Group(
                    "G2",
                    instructionList = listOf(
                        SkipInstruction(code = "skip_to_G3_from_G2", skipToComponent = "G3"),
                        SkipInstruction(code = "skip_to_Q11_from_G2", skipToComponent = "Q11")

                    ),
                    questions = listOf(
                        Question("Q5"),
                        Question("Q6"),
                        Question(
                            "Q7",
                            instructionList = listOf(
                                SkipInstruction(code = "skip_to_G3_from_Q7", skipToComponent = "G3"),
                                SkipInstruction(code = "skip_to_G4_from_Q7", skipToComponent = "G4"),
                                SkipInstruction(code = "skip_to_Q11_from_Q7", skipToComponent = "Q11")

                            )
                        )
                    )
                ),
                Group(
                    "G3",
                    questions = listOf(
                        Question("Q8"),
                        Question("Q9"),
                        Question("Q10")
                    )
                ),
                Group(
                    "G4",
                    questions = listOf(
                        Question("Q11"),
                        Question("Q12"),
                        Question(
                            "Q13",
                            instructionList = listOf(
                                SkipInstruction(code = "skip_to_G1_from_Q13", skipToComponent = "G1"),

                                )
                        )
                    )
                )
            )
        )

        val contextManager = ContextBuilder(mutableListOf(Survey), getValidate())
        contextManager.validate()
        val allSkip = contextManager.components.nestedComponents()
            .map { it.instructionList.filterIsInstance<SkipInstruction>() }
            .flatten()
            .map { skipInstruction ->
                Pair(
                    skipInstruction.code,
                    skipInstruction.errors.any { it is InstructionError.InvalidSkipReference })
            }
        assertEquals(
            listOf(
                Pair("skip_to_G3_from_G1", false),
                Pair("skip_to_Q8_from_Q1", false),
                Pair("skip_to_G3_from_G2", true),
                Pair("skip_to_Q11_from_G2", false),
                Pair("skip_to_G3_from_Q7", true),
                Pair("skip_to_G4_from_Q7", false),
                Pair("skip_to_Q11_from_Q7", false),
                Pair("skip_to_G1_from_Q13", true)
            ), allSkip
        )

    }
}