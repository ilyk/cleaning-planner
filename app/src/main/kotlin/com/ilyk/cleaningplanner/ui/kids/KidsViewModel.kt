package com.ilyk.cleaningplanner.ui.kids

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilyk.cleaningplanner.data.repository.PlanRepository
import com.ilyk.cleaningplanner.domain.model.CleaningMode
import com.ilyk.cleaningplanner.domain.model.Task
import com.ilyk.cleaningplanner.state.PrefsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

private const val KIDS_CARD_LIMIT = 6

data class KidsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val rooms: List<String> = emptyList(),
    val selectedRoom: String? = null,
    val tasks: List<Task> = emptyList(),
    val completedIds: Set<String> = emptySet(),
    val showCelebration: Boolean = false
) {
    val pendingTasks: List<Task> get() = tasks.filter { it.id !in completedIds }
    val totalForRoom: Int get() = tasks.size
    val doneCount: Int get() = completedIds.size
    val allDone: Boolean get() = tasks.isNotEmpty() && pendingTasks.isEmpty()
}

@HiltViewModel
class KidsViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val prefsStore: PrefsStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(KidsUiState())
    val uiState: StateFlow<KidsUiState> = _uiState.asStateFlow()

    // Declared before the init block so the field initializer runs first; otherwise
    // a fast-completing loadRooms() inside `init` could write here and then have its
    // value clobbered by the field initializer that runs in declaration order.
    private var _allTasks: List<Task> = emptyList()

    init {
        loadRooms()
    }

    fun onSelectRoom(room: String) {
        val tasksForRoom = filterRoomTasks(_allTasks, room)
        _uiState.value = _uiState.value.copy(
            selectedRoom = room,
            tasks = tasksForRoom,
            completedIds = emptySet(),
            showCelebration = false
        )
    }

    fun onBackToRoomPicker() {
        _uiState.value = _uiState.value.copy(
            selectedRoom = null,
            tasks = emptyList(),
            completedIds = emptySet(),
            showCelebration = false
        )
    }

    fun onTaskDone(taskId: String) {
        val state = _uiState.value
        if (taskId in state.completedIds) return
        val newCompleted = state.completedIds + taskId
        val tasksForRoom = state.tasks
        val finishedRoom = tasksForRoom.isNotEmpty() && tasksForRoom.all { it.id in newCompleted }
        _uiState.value = state.copy(
            completedIds = newCompleted,
            showCelebration = finishedRoom
        )
        viewModelScope.launch {
            runCatching { planRepository.markTaskDone(taskId) }
        }
    }

    fun onDismissCelebration() {
        _uiState.value = _uiState.value.copy(showCelebration = false)
    }

    private fun loadRooms() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val homeId = prefsStore.homeIdFlow.first()
            val modeName = prefsStore.currentModeFlow.first()
            val mode = runCatching { CleaningMode.valueOf(modeName) }.getOrDefault(CleaningMode.FOCUS)
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

            if (homeId == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "No home configured — complete onboarding first."
                )
                return@launch
            }

            runCatching { planRepository.getToday(homeId, today.toString(), mode) }
                .onSuccess { plan ->
                    _allTasks = plan.tasks
                    val rooms = plan.tasks.mapNotNull { it.roomId }.distinct().take(8)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = null,
                        rooms = rooms
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load today's tasks."
                    )
                }
        }
    }

    private fun filterRoomTasks(all: List<Task>, room: String): List<Task> =
        all.filter { it.roomId == room }.take(KIDS_CARD_LIMIT)
}
