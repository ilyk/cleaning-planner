package com.ilyk.cleaningplanner.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilyk.cleaningplanner.data.repository.PlanRepository
import com.ilyk.cleaningplanner.domain.model.CleaningMode
import com.ilyk.cleaningplanner.domain.model.Plan
import com.ilyk.cleaningplanner.state.PrefsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val plan: Plan? = null,
    val error: String? = null,
    val currentMode: CleaningMode = CleaningMode.FOCUS,
    val isOptimizing: Boolean = false,
    val optimizeError: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val prefsStore: PrefsStore
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        loadPlan()
    }
    
    fun loadPlan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val now = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                val todayIso = "%04d-%02d-%02d".format(
                    now.date.year,
                    now.date.monthNumber,
                    now.date.dayOfMonth
                )
                
                val plan = planRepository.getToday(
                    homeId = "default_home",
                    date = todayIso,
                    mode = _uiState.value.currentMode
                )
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    plan = plan
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }
    
    fun refreshPlan() {
        loadPlan()
    }
    
    fun changeMode(mode: CleaningMode) {
        _uiState.value = _uiState.value.copy(currentMode = mode)
        loadPlan()
    }
    
    fun completeTask(taskId: String) {
        viewModelScope.launch {
            val deviceId = prefsStore.getOrCreateDeviceId()
            planRepository.markTaskDone(taskId, deviceId)
            // Sync history after task completion (best-effort, non-blocking)
            launch {
                try {
                    val currentPlan = _uiState.value.plan
                    if (currentPlan != null) {
                        planRepository.syncHistoryBatch(currentPlan.homeId, deviceId)
                    }
                } catch (e: Exception) {
                    // Silently fail - history sync is best-effort
                }
            }
            loadPlan()
        }
    }
    
    fun skipTask(taskId: String) {
        viewModelScope.launch {
            val deviceId = prefsStore.getOrCreateDeviceId()
            planRepository.markTaskSkipped(taskId, deviceId)
            // Sync history after task skip (best-effort, non-blocking)
            launch {
                try {
                    val currentPlan = _uiState.value.plan
                    if (currentPlan != null) {
                        planRepository.syncHistoryBatch(currentPlan.homeId, deviceId)
                    }
                } catch (e: Exception) {
                    // Silently fail - history sync is best-effort
                }
            }
            loadPlan()
        }
    }
    
    fun assignTask(taskId: String, memberId: String) {
        viewModelScope.launch {
            planRepository.assignTask(taskId, memberId)
            loadPlan()
        }
    }
    
    fun optimizePlan() {
        viewModelScope.launch {
            val currentPlan = _uiState.value.plan ?: return@launch
            
            _uiState.value = _uiState.value.copy(
                isOptimizing = true,
                optimizeError = null
            )
            
            try {
                val now = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                val todayIso = "%04d-%02d-%02d".format(
                    now.date.year,
                    now.date.monthNumber,
                    now.date.dayOfMonth
                )
                
                val result = planRepository.optimizePlanWithBackend(
                    homeId = currentPlan.homeId,
                    date = todayIso,
                    mode = _uiState.value.currentMode,
                    reasons = "User requested optimization from Home screen"
                )
                
                val optimizedPlan = result.getOrNull()
                if (optimizedPlan != null) {
                    _uiState.value = _uiState.value.copy(
                        isOptimizing = false,
                        plan = optimizedPlan,
                        optimizeError = null
                    )
                } else {
                    val error = result.exceptionOrNull()
                    _uiState.value = _uiState.value.copy(
                        isOptimizing = false,
                        optimizeError = error?.message ?: "Optimization failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isOptimizing = false,
                    optimizeError = e.message ?: "Unknown error during optimization"
                )
            }
        }
    }
}
