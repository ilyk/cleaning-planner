package com.ilyk.cleaningplanner.core.model.domain

import kotlinx.serialization.Serializable

@Serializable
data class Member(
    val id: String,
    val name: String,
    val color: Long,
    val emoji: String
)
