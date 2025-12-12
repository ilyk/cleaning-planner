package com.ilyk.cleaningplanner.core.model.domain

import kotlinx.serialization.Serializable

@Serializable
enum class Mode {
    Focus,      // <=15 min tasks
    LowEnergy,  // reduce count
    FullReset,  // full list
    PetMode     // inject pet-related tasks
}
