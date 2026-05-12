package com.ilyk.cleaningplanner.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.WorkManager
import com.ilyk.cleaningplanner.state.PrefsStore
import com.ilyk.cleaningplanner.ui.components.CleanFlowBottomBar
import com.ilyk.cleaningplanner.worker.DailyPlanWorker
import com.ilyk.cleaningplanner.ui.components.CleanFlowTabRoutes
import com.ilyk.cleaningplanner.ui.home.HomeScreen
import com.ilyk.cleaningplanner.ui.insights.AiInsightsScreen
import com.ilyk.cleaningplanner.ui.kids.KidsScreen
import com.ilyk.cleaningplanner.ui.planner.PlannerScreen
import com.ilyk.cleaningplanner.ui.voice.ClaraVoiceScreen
import com.ilyk.cleaningplanner.ui.task.TaskDetailScreen
import com.ilyk.cleaningplanner.ui.family.FamilyModeScreen
import com.ilyk.cleaningplanner.ui.settings.SettingsScreen
import com.ilyk.cleaningplanner.feature.clara.chat.ClaraTextChatScreen
import com.ilyk.cleaningplanner.feature.setup.screens.SetupWizardScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val prefsStore: PrefsStore,
    private val workManager: WorkManager
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination

    init {
        // Idempotent — `KEEP` policy means re-schedule on every app start is a no-op
        // once the worker is already enqueued. Worker itself bails early when
        // `prefsStore.homeIdFlow.first() == null`, so pre-onboarding starts are safe.
        DailyPlanWorker.schedule(workManager)

        viewModelScope.launch {
            val isOnboarded = prefsStore.isOnboardingCompleted.first()
            // Clara is the primary onboarding path. When the user has disabled Clara
            // (or a future build flips this for missing API keys), fall back to the
            // manual setup wizard from :feature:setup.
            val claraAvailable = prefsStore.isClaraEnabled.first()
            _startDestination.value = when {
                isOnboarded -> Screen.Home.route
                claraAvailable -> Screen.Onboarding.route
                else -> Screen.ManualSetup.route
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
    data object Planner : Screen("planner")
    data object Family : Screen("family")
    data object Kids : Screen("kids")
    data object Ai : Screen("ai")
    data object Voice : Screen("voice")
    data object TextChat : Screen("text_chat")
    data object Onboarding : Screen("onboarding")
    data object ManualSetup : Screen("manual_setup")
    data object TaskDetail : Screen("task_detail")
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

        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val showBottomBar = currentRoute in CleanFlowTabRoutes

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    CleanFlowBottomBar(
                        currentRoute = currentRoute,
                        onSelect = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = startDestination!!,
                modifier = Modifier.padding(padding)
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
                            navController.navigate(Screen.Family.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToSettings = {
                            navController.navigate(Screen.Settings.route)
                        }
                    )
                }

                composable(Screen.Planner.route) {
                    PlannerScreen()
                }

                composable(Screen.Family.route) {
                    FamilyModeScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable(Screen.Kids.route) {
                    KidsScreen()
                }

                composable(Screen.Ai.route) {
                    AiInsightsScreen()
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

                // Manual setup wizard — reached when Clara is unavailable
                composable(Screen.ManualSetup.route) {
                    SetupWizardScreen(
                        onSetupComplete = { homeId ->
                            viewModel.completeOnboarding(homeId)
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.ManualSetup.route) { inclusive = true }
                            }
                        },
                        onBack = {
                            // No back-out during onboarding — wizard handles its own internal back nav.
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
}

