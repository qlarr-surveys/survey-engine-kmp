package com.qlarr.surveyengine.common

import com.qlarr.scriptengine.getValidate
import com.qlarr.surveyengine.usecase.ScriptEngineValidate

actual fun buildScriptEngine(): ScriptEngineValidate  = getValidate()