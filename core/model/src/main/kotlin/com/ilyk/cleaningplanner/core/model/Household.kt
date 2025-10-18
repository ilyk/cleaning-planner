package com.ilyk.cleaningplanner.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Household(
    val id: String,
    val name: String,
    val inviteCode: String
)

