package com.ilyk.cleaningplanner.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ilyk.cleaningplanner.state.PrefsStore
import com.ilyk.cleaningplanner.ui.home.HomeScreen
import com.ilyk.cleaningplanner.ui.voice.ClaraVoiceScreen
import com.ilyk.cleaningplanner.ui.task.TaskDetailScreen
import com.ilyk.cleaningplanner.ui.family.FamilyModeScreen
import com.ilyk.cleaningplanner.ui.settings.SettingsScreen
import com.ilyk.cleaningplanner.feature.clara.chat.ClaraTextChatScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val prefsStore: PrefsStore
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination

    init {
        viewModelScope.launch {
            val isOnboarded = prefsStore.isOnboardingCompleted.first()
            _startDestination.value = if (isOnboarded) {
                Screen.Home.route
            } else {
                Screen.Onboarding.route
            }
        }
    }

    fun completeOnboarding(homeId: String?) {
        viewModelScope.launch {
            prefsStore.setOnboardingCompleted(true, homeId)
        }
    }
}

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Voice : Screen("voice")
    data object TextChat : Screen("text_chat")
    data object Onboarding : Screen("onboarding")
    data object TaskDetail : Screen("task_detail")
    data object Family : Screen("family")
    data object Settings : Screen("settings")
}

@Composable
fun CleaningPlannerNavHost(
    modifier: Modifier = Modifier,
    viewModel: NavigationViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val startDestination by viewModel.startDestination.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        // Show loading while determining start destination
        if (startDestination == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Box
        }

        NavHost(
            navController = navController,
            startDestination = startDestination!!
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToVoice = {
                        navController.navigate(Screen.Voice.route)
                    },
                    onNavigateToTextChat = {
                        navController.navigate(Screen.TextChat.route)
                    },
                    onNavigateToTaskDetail = { taskId ->
                        navController.navigate("${Screen.TaskDetail.route}/$taskId")
                    },
                    onNavigateToFamily = {
                        navController.navigate(Screen.Family.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }
            
            composable(Screen.Voice.route) {
                ClaraVoiceScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.TextChat.route) {
                ClaraTextChatScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onOnboardingComplete = { result ->
                        // Navigate to home with onboarding data
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.TextChat.route) { inclusive = true }
                        }
                    }
                )
            }

            // Onboarding starts with text chat
            composable(Screen.Onboarding.route) {
                ClaraTextChatScreen(
                    onNavigateBack = {
                        // Don't allow back during onboarding
                    },
                    onOnboardingComplete = { result ->
                        viewModel.completeOnboarding(result.homeId)
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
            
            composable("${Screen.TaskDetail.route}/{taskId}") { backStackEntry ->
                val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
                TaskDetailScreen(
                    taskId = taskId,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            composable(Screen.Family.route) {
                FamilyModeScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

