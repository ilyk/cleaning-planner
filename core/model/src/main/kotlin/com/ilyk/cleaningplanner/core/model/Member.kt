package com.ilyk.cleaningplanner.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Member(
    val id: String,
    val role: Role,
    val nickname: String? = null,
    val colorHex: String? = null,
    val userId: String,
    val householdId: String
)

