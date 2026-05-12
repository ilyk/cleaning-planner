package com.ilyk.cleaningplanner.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilyk.cleaningplanner.data.repository.PlanRepository
import com.ilyk.cleaningplanner.domain.model.CleaningMode
import com.ilyk.cleaningplanner.domain.model.Plan
import com.ilyk.cleaningplanner.domain.model.Task
import com.ilyk.cleaningplanner.domain.model.TaskPriority
import com.ilyk.cleaningplanner.state.PrefsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

enum class PlannerTab { Daily, Weekly, Seasonal, Custom }

data class PlannerUiState(
    val selectedTab: PlannerTab = PlannerTab.Daily,
    val isLoading: Boolean = true,
    val error: String? = null,
    val plan: Plan? = null,
    val customTasks: List<Task> = emptyList(),
    val today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    val mode: CleaningMode = CleaningMode.FOCUS
) {
    /** Daily-tab grouping. */
    val tasksByPriority: Map<TaskPriority, List<Task>>
        get() {
            val all = (plan?.tasks.orEmpty()) + customTasks
            return all.groupBy { it.priority }
        }
}

@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val prefsStore: PrefsStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlannerUiState())
    val uiState: StateFlow<PlannerUiState> = _uiState.asStateFlow()

    init {
        loadToday()
    }

    fun onTabChange(tab: PlannerTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun refresh() = loadToday()

    fun onAddCustomTask(
        title: String,
        roomId: String?,
        estimatedDurationMinutes: Int,
        priority: TaskPriority
    ) {
        val task = planRepository.addCustomTask(
            title = title.trim(),
            roomId = roomId?.takeIf { it.isNotBlank() },
            estimatedDurationMinutes = estimatedDurationMinutes.coerceIn(1, 240),
            priority = priority
        )
        _uiState.value = _uiState.value.copy(customTasks = _uiState.value.customTasks + task)
    }

    fun onCompleteTask(taskId: String) {
        viewModelScope.launch {
            runCatching { planRepository.markTaskDone(taskId) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
        }
    }

    fun onSkipTask(taskId: String) {
        viewModelScope.launch {
            runCatching { planRepository.markTaskSkipped(taskId) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(error = e.message) }
        }
    }

    private fun loadToday() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val homeId = prefsStore.homeIdFlow.first()
            val modeName = prefsStore.currentModeFlow.first()
            val mode = runCatching { CleaningMode.valueOf(modeName) }.getOrDefault(CleaningMode.FOCUS)
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

            if (homeId == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "No home configured — complete onboarding first.",
                    mode = mode,
                    today = today
                )
                return@launch
            }

            runCatching { planRepository.getToday(homeId, today.toString(), mode) }
                .onSuccess { plan ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = null,
                        plan = plan,
                        mode = mode,
                        today = today
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load today's plan.",
                        mode = mode,
                        today = today
                    )
                }
        }
    }
}
