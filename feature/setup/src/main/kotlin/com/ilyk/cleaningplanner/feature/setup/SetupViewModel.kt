package com.ilyk.cleaningplanner.feature.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject

/**
 * Setup wizard steps
 */
enum class SetupStep {
    HOME_NAME,
    ROOMS,
    MEMBERS,
    PETS,
    PREFERENCES,
    REVIEW
}

/**
 * UI state for setup wizard
 */
data class SetupUiState(
    val currentStep: SetupStep = SetupStep.HOME_NAME,
    val homeName: String = "",
    val rooms: List<SetupRoom> = emptyList(),
    val members: List<SetupMember> = emptyList(),
    val pets: List<SetupPet> = emptyList(),
    val preferences: SetupPreferences = SetupPreferences(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * One-time events from setup wizard
 */
sealed class SetupEvent {
    data class NavigateToHome(val homeId: String) : SetupEvent()
    data class ShowError(val message: String) : SetupEvent()
}

/**
 * ViewModel for the setup wizard flow
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val setupRepository: SetupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SetupEvent>()
    val events = _events.asSharedFlow()

    // Track if user wants to skip certain optional steps
    private var skipPets = false
    private var skipPreferences = false

    fun setHomeName(name: String) {
        _uiState.update { it.copy(homeName = name) }
    }

    // Room management
    fun addRoom(name: String, kind: RoomKind, notes: String = "") {
        val room = SetupRoom(
            id = UUID.randomUUID().toString(),
            name = name,
            kind = kind,
            notes = notes
        )
        _uiState.update { it.copy(rooms = it.rooms + room) }
    }

    fun updateRoom(roomId: String, name: String, kind: RoomKind, notes: String = "") {
        _uiState.update { state ->
            state.copy(rooms = state.rooms.map {
                if (it.id == roomId) it.copy(name = name, kind = kind, notes = notes) else it
            })
        }
    }

    fun removeRoom(roomId: String) {
        _uiState.update { it.copy(rooms = it.rooms.filter { room -> room.id != roomId }) }
    }

    // Member management
    fun addMember(name: String, role: MemberRole, notes: String = "") {
        val member = SetupMember(
            id = UUID.randomUUID().toString(),
            name = name,
            role = role,
            notes = notes
        )
        _uiState.update { it.copy(members = it.members + member) }
    }

    fun updateMember(memberId: String, name: String, role: MemberRole, notes: String = "") {
        _uiState.update { state ->
            state.copy(members = state.members.map {
                if (it.id == memberId) it.copy(name = name, role = role, notes = notes) else it
            })
        }
    }

    fun removeMember(memberId: String) {
        _uiState.update { it.copy(members = it.members.filter { member -> member.id != memberId }) }
    }

    // Pet management
    fun addPet(name: String, type: PetType, sheddingLevel: Int = 1) {
        val pet = SetupPet(
            id = UUID.randomUUID().toString(),
            name = name,
            type = type,
            sheddingLevel = sheddingLevel
        )
        _uiState.update { it.copy(pets = it.pets + pet) }
    }

    fun updatePet(petId: String, name: String, type: PetType, sheddingLevel: Int = 1) {
        _uiState.update { state ->
            state.copy(pets = state.pets.map {
                if (it.id == petId) it.copy(name = name, type = type, sheddingLevel = sheddingLevel) else it
            })
        }
    }

    fun removePet(petId: String) {
        _uiState.update { it.copy(pets = it.pets.filter { pet -> pet.id != petId }) }
    }

    // Preferences management
    fun updatePreferences(preferences: SetupPreferences) {
        _uiState.update { it.copy(preferences = preferences) }
    }

    // Navigation
    fun nextStep() {
        _uiState.update { state ->
            val nextStep = when (state.currentStep) {
                SetupStep.HOME_NAME -> SetupStep.ROOMS
                SetupStep.ROOMS -> SetupStep.MEMBERS
                SetupStep.MEMBERS -> if (skipPets) SetupStep.PREFERENCES else SetupStep.PETS
                SetupStep.PETS -> if (skipPreferences) SetupStep.REVIEW else SetupStep.PREFERENCES
                SetupStep.PREFERENCES -> SetupStep.REVIEW
                SetupStep.REVIEW -> SetupStep.REVIEW // Stay on review
            }
            state.copy(currentStep = nextStep, error = null)
        }
    }

    fun previousStep() {
        _uiState.update { state ->
            val prevStep = when (state.currentStep) {
                SetupStep.HOME_NAME -> SetupStep.HOME_NAME // Stay on first
                SetupStep.ROOMS -> SetupStep.HOME_NAME
                SetupStep.MEMBERS -> SetupStep.ROOMS
                SetupStep.PETS -> SetupStep.MEMBERS
                SetupStep.PREFERENCES -> if (skipPets) SetupStep.MEMBERS else SetupStep.PETS
                SetupStep.REVIEW -> if (skipPreferences) SetupStep.PETS else SetupStep.PREFERENCES
            }
            state.copy(currentStep = prevStep, error = null)
        }
    }

    fun skipPetsStep() {
        skipPets = true
        nextStep()
    }

    fun skipPreferencesStep() {
        skipPreferences = true
        nextStep()
    }

    fun canProceed(): Boolean {
        val state = _uiState.value
        return when (state.currentStep) {
            SetupStep.HOME_NAME -> state.homeName.isNotBlank()
            SetupStep.ROOMS -> state.rooms.isNotEmpty()
            SetupStep.MEMBERS -> true // Members are optional
            SetupStep.PETS -> true // Pets are optional
            SetupStep.PREFERENCES -> true // Preferences are optional
            SetupStep.REVIEW -> true
        }
    }

    /**
     * Submit the setup to the backend
     */
    fun submitSetup() {
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val request = ManualSetupRequest(
                    homeName = state.homeName,
                    timezone = TimeZone.getDefault().id,
                    locale = java.util.Locale.getDefault().toLanguageTag(),
                    rooms = state.rooms.map { room ->
                        RoomRequest(
                            name = room.name,
                            kind = room.kind.name.lowercase(),
                            notes = room.notes.takeIf { it.isNotBlank() }
                        )
                    },
                    members = state.members.map { member ->
                        MemberRequest(
                            name = member.name,
                            role = member.role.name.lowercase(),
                            notes = member.notes.takeIf { it.isNotBlank() }
                        )
                    },
                    pets = state.pets.takeIf { it.isNotEmpty() }?.map { pet ->
                        PetRequest(
                            name = pet.name,
                            type = pet.type.name.lowercase(),
                            sheddingLevel = pet.sheddingLevel,
                            frequentRooms = pet.frequentRooms.takeIf { it.isNotEmpty() }
                        )
                    },
                    preferences = PreferencesRequest(
                        preferredCleaningTimes = state.preferences.preferredCleaningTimes.takeIf { it.isNotEmpty() },
                        quietHours = state.preferences.quietHours,
                        busyDays = state.preferences.busyDays.takeIf { it.isNotEmpty() }
                    ).takeIf {
                        it.preferredCleaningTimes != null || it.quietHours != null || it.busyDays != null
                    }
                )

                val response = setupRepository.submitSetup(request)

                if (response.success) {
                    _events.emit(SetupEvent.NavigateToHome(response.homeId))
                } else {
                    _uiState.update { it.copy(error = "Setup failed. Please try again.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Unknown error occurred") }
                _events.emit(SetupEvent.ShowError(e.message ?: "Setup failed"))
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
