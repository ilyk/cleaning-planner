package com.ilyk.cleaningplanner.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Member(
    val id: String,
    val name: String,
    val color: Long,
    val emoji: String
)
