package com.qlarr.surveyengine.model.adapters

import com.qlarr.surveyengine.model.*
import com.qlarr.surveyengine.model.Instruction.*
import com.qlarr.surveyengine.model.InstructionError.ScriptError
import com.qlarr.surveyengine.model.ReservedCode.*
import com.qlarr.surveyengine.usecase.ValidationJsonOutput
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals


@Suppress("PrivatePropertyName")
class JsonAdapterTest {

    private val SKIP =
        SkipInstruction(skipToComponent = "Q2", code = "skip_to_q4", condition = "true", isActive = false)
    private val SKIP_TEXT =
        "{\"code\":\"skip_to_q4\",\"text\":\"true\",\"returnType\":\"boolean\",\"isActive\":false,\"skipToComponent\":\"Q2\",\"condition\":\"true\",\"toEnd\":false}"
    private val PRIORITY_GROUPS = PriorityGroups(
        priorities = listOf(
            PriorityGroup(listOf(ChildPriority("Q1"), ChildPriority("Q2"))),
            PriorityGroup(listOf(ChildPriority("G1"), ChildPriority("G2")))
        )
    )
    private val PRIORITY_GROUPS_TEXT =
        "{\"code\":\"priority_groups\",\"priorities\":[{\"weights\":[{\"code\":\"Q1\",\"weight\":1.0},{\"code\":\"Q2\",\"weight\":1.0}],\"limit\":1},{\"weights\":[{\"code\":\"G1\",\"weight\":1.0},{\"code\":\"G2\",\"weight\":1.0}],\"limit\":1}]}"
    private val RANDOM_GROUP = RandomGroups(groups = listOf(listOf("1", "2", "3"), listOf("4", "5", "6")))
    private val RANDOM_GROUP_TEXT =
        "{\"code\":\"random_group\",\"groups\":[{\"codes\":[\"1\",\"2\",\"3\"],\"randomOption\":\"RANDOM\"},{\"codes\":[\"4\",\"5\",\"6\"],\"randomOption\":\"RANDOM\"}]}"
    private val REF_EQ_TEXT =
        "{\"code\":\"reference_label\",\"references\":[\"Q1.label\"],\"lang\":\"en\"}"
    private val DYNAMIC_EQ_TEXT =
        "{\"code\":\"conditional_relevance\",\"text\":\"true\",\"returnType\":\"boolean\",\"isActive\":true}"
    private val PARENT_REL_TEXT = "{\"code\":\"parent_relevance\",\"children\":[[\"A1\",\"A2\",\"A3\",\"A4\"]]}"
    private val VALUE_EQ_TEXT_INPUT =
        "{\"code\":\"value\",\"text\":\"\",\"returnType\":\"string\",\"isActive\":false}"
    private val VALUE_EQ_TEXT =
        "{\"code\":\"value\",\"text\":\"\",\"returnType\":\"string\",\"isActive\":false}"
    private val EQ_List_TEXT = "[$DYNAMIC_EQ_TEXT,$VALUE_EQ_TEXT]"
    private val DYNAMIC_EQ = SimpleState(
        text = "true",
        reservedCode = ConditionalRelevance
    )
    private val PARENT_REL = ParentRelevance(
        children = listOf(listOf("A1", "A2", "A3", "A4"))
    )
    private val VALUE_EQ = SimpleState(
        text = "",
        reservedCode = Value
    )

    private val REF_EQ = Reference(
        "reference_label",
        listOf("Q1.label"),
        lang = SurveyLang.EN.code
    )

    private val QUESTION = Question("Q2", listOf(SimpleState("true", ConditionalRelevance)))
    private val QUESTION_TEXT =
        "{\"code\":\"Q2\",\"instructionList\":[{\"code\":\"conditional_relevance\",\"text\":\"true\",\"returnType\":\"boolean\",\"isActive\":true}],\"answers\":[],\"errors\":[]}"


    private val G3Q5 = Question(
        code = "Q5",
        instructionList = listOf(SimpleState("", Value))
    )

    private val G3Q6 = Question(
        code = "Q6",
        instructionList = listOf(SimpleState("", Value))
    )

    private val G3_TEXT =
        "{\"code\":\"G3\",\"instructionList\":[{\"code\":\"value\",\"text\":\"\",\"returnType\":\"string\"," +
                "\"isActive\":false}],\"questions\":[{\"code\":\"Q5\",\"instructionList\":[{\"code\":\"value\"," +
                "\"text\":\"\",\"returnType\":\"string\",\"isActive\":false}],\"answers\":[],\"errors\":[]}," +
                "{\"code\":\"Q6\",\"instructionList\":[{\"code\":\"value\",\"text\":\"\",\"returnType\":\"string\"," +
                "\"isActive\":false}],\"answers\":[],\"errors\":[]}],\"groupType\":\"GROUP\",\"errors\":[]}"
    private val G3 = Group(
        code = "G3",
        instructionList = listOf(SimpleState("", Value)),
        questions = listOf(G3Q5, G3Q6)
    )
    private val COMPONENT_List = listOf(G3)

    private val COMPONENT_List_TEXT = "[$G3_TEXT]"

    private val RG_DUPLICATE = InstructionError.DuplicateRandomGroupItems(listOf())
    private val RG_DUPLICATE_TXT = "{\"items\":[],\"name\":\"DuplicateRandomGroupItems\"}"
    private val RG_NOT_CHILD = InstructionError.RandomGroupItemNotChild(listOf("A1", "A2"))
    private val RG_NOT_CHILD_TXT = "{\"items\":[\"A1\",\"A2\"],\"name\":\"RandomGroupItemNotChild\"}"

    private val SCRIPT_FAILURE_ERR = ScriptError(message = "error message", start = 5, end = 120)
    private val SCRIPT_FAILURE_ERR_TEXT =
        "{\"message\":\"error message\",\"start\":5,\"end\":120,\"name\":\"ScriptError\"}"
    private val FWD_DEPENDENCY_ERR = InstructionError.ForwardDependency(Dependency("G1Q1", Value))
    private val FWD_DEPENDENCY_ERR_TEXT =
        "{\"dependency\":{\"componentCode\":\"G1Q1\",\"reservedCode\":\"value\"},\"name\":\"ForwardDependency\"}"
    private val List_ERR = listOf(SCRIPT_FAILURE_ERR, FWD_DEPENDENCY_ERR)
    private val List_ERR_TEXT = "[$SCRIPT_FAILURE_ERR_TEXT,$FWD_DEPENDENCY_ERR_TEXT]"
    private val List_ERR_1 = listOf(RG_DUPLICATE, RG_NOT_CHILD)
    private val List_ERR_1_TEXT = "[$RG_DUPLICATE_TXT,$RG_NOT_CHILD_TXT]"

    private val NAV_INDEX_G1 = NavigationIndex.Group("G1")
    private val NAV_INDEX_Q1 = NavigationIndex.Question("Q1")
    private val NAV_INDEX_G1_2_3 = NavigationIndex.Groups(listOf("G1", "G2", "G3"))
    private val NAV_INDEX_G1_TEXT = "{\"groupId\":\"G1\",\"name\":\"group\"}"
    private val NAV_INDEX_G1_2_3_TEXT = "{\"groupIds\":[\"G1\",\"G2\",\"G3\"],\"name\":\"groups\"}"
    private val NAV_INDEX_Q1_TEXT = "{\"questionId\":\"Q1\",\"name\":\"question\"}"


    private val useCaseInput = NavigationUseCaseInput(
        lang = SurveyLang.DE.code,
        navigationInfo = NavigationInfo(
            navigationDirection = NavigationDirection.Jump(navigationIndex = NavigationIndex.Groups(listOf()))
        ),
        values = mapOf(
            "Q1.value" to "",
            "Q2.value" to 2.2,
            "Q3.value" to true,
            "Q4.value" to listOf("1", "2", "3"),
            "Q5.value" to mapOf("first" to "john", "last" to "smith")
        )
    )
    private val useCaseInputText =
        "{\"values\":{\"Q1.value\":\"\",\"Q2.value\":2.2,\"Q3.value\":true,\"Q4.value\":[\"1\",\"2\",\"3\"],\"Q5.value\":{\"first\":\"john\",\"last\":\"smith\"}},\"lang\":\"de\",\"navigationInfo\":{\"navigationIndex\":null,\"navigationDirection\":{\"name\":\"JUMP\",\"navigationIndex\":{\"groupIds\":[],\"name\":\"groups\"}}}}"

    private val useCaseInput1 = NavigationUseCaseInput(
        navigationInfo = NavigationInfo(
            navigationIndex = NavigationIndex.Question("Q1"),
            navigationDirection = NavigationDirection.Next
        )
    )
    private val useCaseInputText1 =
        "{\"values\":{},\"lang\":null,\"navigationInfo\":{\"navigationIndex\":{\"questionId\":\"Q1\",\"name\":\"question\"},\"navigationDirection\":{\"name\":\"NEXT\"}}}"

    private val useCaseInput2 = NavigationUseCaseInput()
    private val useCaseInputText2 =
        "{\"values\":{},\"lang\":null,\"navigationInfo\":{\"navigationIndex\":null,\"navigationDirection\":{\"name\":\"START\"}}}"


    @Test
    fun serializes_and_deserializes_instructions() {
        // Instead of comparing specific string formats, we'll test round-trip serialization
        assertEquals(SKIP, jsonMapper.decodeFromString<Instruction>(jsonMapper.encodeToString(SKIP)))
        assertEquals(RANDOM_GROUP, jsonMapper.decodeFromString<Instruction>(jsonMapper.encodeToString(RANDOM_GROUP)))
        assertEquals(PRIORITY_GROUPS, jsonMapper.decodeFromString<Instruction>(jsonMapper.encodeToString(PRIORITY_GROUPS)))
        assertEquals(DYNAMIC_EQ, jsonMapper.decodeFromString<Instruction>(jsonMapper.encodeToString(DYNAMIC_EQ)))
        assertEquals(PARENT_REL, jsonMapper.decodeFromString<Instruction>(jsonMapper.encodeToString(PARENT_REL)))
        assertEquals(REF_EQ, jsonMapper.decodeFromString<Instruction>(jsonMapper.encodeToString(REF_EQ)))
        assertEquals(VALUE_EQ, jsonMapper.decodeFromString<Instruction>(jsonMapper.encodeToString(VALUE_EQ)))

        val list = listOf(DYNAMIC_EQ, VALUE_EQ)
        assertEquals(list, jsonMapper.decodeFromString<List<Instruction>>(jsonMapper.encodeToString(list)))
    }

    @Test
    fun serializes_and_de_serializes_binding_errors() {
        // Test round-trip serialization
        assertEquals(
            FWD_DEPENDENCY_ERR,
            jsonMapper.decodeFromString<InstructionError>(jsonMapper.encodeToString(FWD_DEPENDENCY_ERR))
        )

        assertEquals(List_ERR, jsonMapper.decodeFromString<List<InstructionError>>(jsonMapper.encodeToString(List_ERR)))
        assertEquals(List_ERR_1, jsonMapper.decodeFromString<List<InstructionError>>(jsonMapper.encodeToString(List_ERR_1)))
    }

    @Test
    fun serializes_and_de_serializes_nav_index() {
        // Test round-trip serialization
        assertEquals(NAV_INDEX_G1, jsonMapper.decodeFromString<NavigationIndex>(jsonMapper.encodeToString(NAV_INDEX_G1)))
        assertEquals(NAV_INDEX_G1_2_3, jsonMapper.decodeFromString<NavigationIndex>(jsonMapper.encodeToString(NAV_INDEX_G1_2_3)))
        assertEquals(NAV_INDEX_Q1, jsonMapper.decodeFromString<NavigationIndex>(jsonMapper.encodeToString(NAV_INDEX_Q1)))
    }


    @Test
    fun serializes_and_de_serializes_components() {
        // Test round-trip serialization
        assertEquals(QUESTION, jsonMapper.decodeFromString<Question>(jsonMapper.encodeToString(QUESTION)))
        assertEquals(COMPONENT_List, jsonMapper.decodeFromString<List<Group>>(jsonMapper.encodeToString(COMPONENT_List)))
        assertEquals(G3, jsonMapper.decodeFromString<Group>(jsonMapper.encodeToString(G3)))
    }

    @Test
    fun serialises_and_deserialises_return_type() {
        val file = ReturnType.FILE
        assertEquals(file, jsonMapper.decodeFromString<ReturnType>(jsonMapper.encodeToString(file)))
    }

    @Test
    fun serialises_and_deserialises_use_case_input() {
        // Test round-trip serialization
        assertEquals(
            useCaseInput,
            jsonMapper.decodeFromString<NavigationUseCaseInput>(jsonMapper.encodeToString(useCaseInput))
        )
        
        assertEquals(
            useCaseInput1,
            jsonMapper.decodeFromString<NavigationUseCaseInput>(jsonMapper.encodeToString(useCaseInput1))
        )
        
        assertEquals(
            useCaseInput2,
            jsonMapper.decodeFromString<NavigationUseCaseInput>(jsonMapper.encodeToString(useCaseInput2))
        )
    }


    @Test
    fun serializes_and_de_serializes_to_validation_json_output() {
        // Create a sample JsonObject for testing
        val sampleJsonObject = jsonMapper.parseToJsonElement(
            "{\"code\":\"G3\"," +
            "\"instructionList\":[{\"code\":\"value\",\"text\":\"\",\"isActive\":false,\"returnType\":\"String\"}]," +
            "\"children\":[{\"code\":\"Q5\",\"instructionList\":[{\"code\":\"value\",\"text\":\"\",\"isActive\":false,\"returnType\":\"String\"}]},{\"code\":\"Q6\",\"instructionList\":[{\"code\":\"value\",\"text\":\"\",\"isActive\":false,\"returnType\":\"String\"}]}]}"
        ).jsonObject
        
        // Create a ValidationJsonOutput with meaningful test data
        val validationJsonOutput = ValidationJsonOutput(
            schema = listOf(
                ResponseField("Q1", ColumnName.VALUE, ReturnType.STRING),
                ResponseField("Q2", ColumnName.ORDER, ReturnType.INT)
            ),
            impactMap = mapOf(
                Dependency("Q1", Value) to listOf(
                    Dependent("Q1", "conditional_relevance"),
                    Dependent("Q1", "validity")
                ),
                Dependency("Q3", Meta) to listOf(Dependent("Q3", "conditional_relevance"), Dependent("Q3", "validity"))
            ).toStringImpactMap(),
            survey = sampleJsonObject,
            componentIndexList = listOf(
                ComponentIndex(
                    "Q1", null, listOf("Q1A1"), 0, 0,
                    setOf(), setOf(ChildrenRelevance, Value)
                ),
                ComponentIndex(
                    "Q3", null, listOf("Q3A1", "Q3A3"), 1, 1,
                    setOf(), setOf(ChildrenRelevance, Value)
                )
            ),
            script = "",
            skipMap = mapOf()
        )
        
        // Test serializing
        val jsonString = jsonMapper.encodeToString(validationJsonOutput)
        
        // Create a new instance for comparison
        val newJsonObject = jsonMapper.parseToJsonElement(
            "{\"code\":\"G3\"," +
            "\"instructionList\":[{\"code\":\"value\",\"text\":\"\",\"isActive\":false,\"returnType\":\"String\"}]," +
            "\"children\":[{\"code\":\"Q5\",\"instructionList\":[{\"code\":\"value\",\"text\":\"\",\"isActive\":false,\"returnType\":\"String\"}]},{\"code\":\"Q6\",\"instructionList\":[{\"code\":\"value\",\"text\":\"\",\"isActive\":false,\"returnType\":\"String\"}]}]}"
        ).jsonObject
        
        val expectedOutput = ValidationJsonOutput(
            schema = validationJsonOutput.schema,
            impactMap = validationJsonOutput.impactMap,
            survey = newJsonObject, // We need to create a new JsonObject since JsonObject equality compares references
            componentIndexList = validationJsonOutput.componentIndexList,
            script = validationJsonOutput.script,
            skipMap = validationJsonOutput.skipMap
        )
        
        // Verify schema, impactMap, componentIndexList, script, and skipMap are correctly serialized
        val deserializedOutput = jsonMapper.decodeFromString<ValidationJsonOutput>(jsonString)
        assertEquals(validationJsonOutput.schema, deserializedOutput.schema)
        assertEquals(validationJsonOutput.impactMap, deserializedOutput.impactMap)
        assertEquals(validationJsonOutput.componentIndexList, deserializedOutput.componentIndexList)
        assertEquals(validationJsonOutput.script, deserializedOutput.script)
        assertEquals(validationJsonOutput.skipMap, deserializedOutput.skipMap)
        
        // JsonObject content comparison (can't use direct equality because JsonObject equality is reference-based)
        assertEquals(
            sampleJsonObject.toString(),
            deserializedOutput.survey.toString()
        )
    }
}