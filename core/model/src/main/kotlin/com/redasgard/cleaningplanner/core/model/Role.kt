package com.redasgard.cleaningplanner.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Role {
    OWNER,
    PARENT,
    KID,
    GUEST
}

