package com.qlarr.surveyengine.model

import kotlinx.serialization.Serializable

@Serializable
data class NavigationInfo(
    val navigationIndex: NavigationIndex? = null,
    val navigationDirection: NavigationDirection = NavigationDirection.Start
)