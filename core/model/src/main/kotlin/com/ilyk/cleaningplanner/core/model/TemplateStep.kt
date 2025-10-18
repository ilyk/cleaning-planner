package com.ilyk.cleaningplanner.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TemplateStep(
    val label: String,
    val estMin: Int = 0
)

