package com.qlarr.surveyengine.usecase

import com.qlarr.surveyengine.common.loadFromResources
import kotlin.test.Test
import kotlin.test.assertEquals

class ChangeCodeUseCaseWrapperImplTest {

    @Test
    fun changeCode() {
        val input = loadFromResources("validationJsonOutput_1.json")

        val beforeCount = countWordOccurrences(input, "Q375xtg")
        val beforeNoCount = countWordOccurrences(input, "Q1")
        val stringOutput = ChangeCodeUseCaseWrapper.create(input)
            .changeCode("Q375xtg", "Q1")
        val afterCount = countWordOccurrences(stringOutput, "Q1")
        val afterNoCount = countWordOccurrences(stringOutput, "Q375xtg")

        assertEquals(beforeCount, afterCount)
        assertEquals(beforeNoCount, afterNoCount)


    }
    @Test
    fun changeCodeWithSkip() {
        val input = loadFromResources("validationJsonOutput_2.json")

        val beforeGCount = countWordOccurrences(input, "G2")
        val beforeQCount = countWordOccurrences(input, "Q408hut")
        val beforeGNoCount = countWordOccurrences(input, "Gend")
        val beforeQNoCount = countWordOccurrences(input, "Q1")
        var stringOutput = ChangeCodeUseCaseWrapper.create(input)
            .changeCode("G2", "Gend")
        stringOutput = ChangeCodeUseCaseWrapper.create(stringOutput)
            .changeCode("Q408hut", "Q1")
        val afterGCount = countWordOccurrences(stringOutput, "Gend")
        val afterQCount = countWordOccurrences(stringOutput, "Q1")
        val afterGNoCount = countWordOccurrences(stringOutput, "G2")
        val afterQNoCount = countWordOccurrences(stringOutput, "Q408hut")

        assertEquals(beforeGCount, afterGCount)
        assertEquals(beforeQCount, afterQCount)
        assertEquals(beforeGNoCount, afterGNoCount)
        assertEquals(beforeQNoCount, afterQNoCount)


    }

    private fun countWordOccurrences(text: String, word: String): Int {
        val regex = "\\b${Regex.escape(word)}\\b".toRegex(RegexOption.IGNORE_CASE)
        return regex.findAll(text).count()
    }
}