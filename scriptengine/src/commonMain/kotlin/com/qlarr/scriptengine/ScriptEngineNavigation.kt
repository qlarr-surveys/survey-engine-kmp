package com.qlarr.scriptengine

import com.qlarr.surveyengine.usecase.ScriptEngineNavigate
import com.qlarr.surveyengine.usecase.ScriptEngineValidate


expect fun getValidate(): ScriptEngineValidate
expect fun getNavigate(script:String): ScriptEngineNavigate
