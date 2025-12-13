package com.ilyk.cleaningplanner.feature.setup

import kotlinx.serialization.Serializable

/**
 * Room kind types that match the backend RoomKind enum
 */
@Serializable
enum class RoomKind {
    Kitchen,
    Bathroom,
    Bedroom,
    Living,
    Other;

    val displayName: String
        get() = when (this) {
            Kitchen -> "Kitchen"
            Bathroom -> "Bathroom"
            Bedroom -> "Bedroom"
            Living -> "Living Room"
            Other -> "Other"
        }
}

/**
 * Member role types that match the backend MemberRole enum
 */
@Serializable
enum class MemberRole {
    Adult,
    Kid,
    Guest,
    PetProxy;

    val displayName: String
        get() = when (this) {
            Adult -> "Adult"
            Kid -> "Kid/Teen"
            Guest -> "Guest"
            PetProxy -> "Pet"
        }
}

/**
 * Pet type for home setup
 */
@Serializable
enum class PetType {
    Dog,
    Cat,
    Bird,
    Fish,
    Other;

    val displayName: String
        get() = when (this) {
            Dog -> "Dog"
            Cat -> "Cat"
            Bird -> "Bird"
            Fish -> "Fish"
            Other -> "Other"
        }
}

/**
 * Room being configured during setup
 */
@Serializable
data class SetupRoom(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val kind: RoomKind,
    val notes: String = ""
)

/**
 * Member being configured during setup
 */
@Serializable
data class SetupMember(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val role: MemberRole,
    val notes: String = ""
)

/**
 * Pet being configured during setup
 */
@Serializable
data class SetupPet(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val type: PetType,
    val sheddingLevel: Int = 1, // 0-3: none, low, medium, high
    val frequentRooms: List<String> = emptyList() // Room IDs
)

/**
 * Schedule preferences during setup
 */
@Serializable
data class SetupPreferences(
    val preferredCleaningTimes: List<String> = listOf("morning"), // morning, afternoon, evening
    val quietHours: String? = null, // e.g., "22:00-08:00"
    val busyDays: List<String> = emptyList() // Monday, Tuesday, etc.
)

/**
 * Complete home setup data - collected from wizard
 */
@Serializable
data class HomeSetupData(
    val homeName: String,
    val timezone: String = "UTC",
    val locale: String = "en-US",
    val rooms: List<SetupRoom>,
    val members: List<SetupMember>,
    val pets: List<SetupPet> = emptyList(),
    val preferences: SetupPreferences = SetupPreferences()
)

/**
 * Request to create/update home via manual setup
 */
@Serializable
data class ManualSetupRequest(
    val homeName: String,
    val timezone: String,
    val locale: String,
    val rooms: List<RoomRequest>,
    val members: List<MemberRequest>,
    val pets: List<PetRequest>? = null,
    val preferences: PreferencesRequest? = null
)

@Serializable
data class RoomRequest(
    val name: String,
    val kind: String,
    val notes: String? = null
)

@Serializable
data class MemberRequest(
    val name: String,
    val role: String,
    val notes: String? = null
)

@Serializable
data class PetRequest(
    val name: String,
    val type: String,
    val sheddingLevel: Int? = null,
    val frequentRooms: List<String>? = null
)

@Serializable
data class PreferencesRequest(
    val preferredCleaningTimes: List<String>? = null,
    val quietHours: String? = null,
    val busyDays: List<String>? = null
)

/**
 * Response from manual setup endpoint
 */
@Serializable
data class ManualSetupResponse(
    val homeId: String,
    val success: Boolean,
    val roomCount: Int,
    val memberCount: Int
)
