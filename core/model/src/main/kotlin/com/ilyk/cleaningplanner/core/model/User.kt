package com.ilyk.cleaningplanner.core.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val email: String,
    val displayName: String? = null,
    val locale: String = "en"
)

