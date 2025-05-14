package com.qlarr.surveyengine.model

import kotlinx.serialization.Serializable

@Serializable
data class SurveyLang(val code: String, val name: String) {

    companion object {
        val EN = SurveyLang("en", "English")

        val DE = SurveyLang("de", "Deutsch")

        val AR = SurveyLang("ar", "العربية")
    }


}

