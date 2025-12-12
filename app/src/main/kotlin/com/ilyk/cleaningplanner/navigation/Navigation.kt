package com.ilyk.cleaningplanner.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ilyk.cleaningplanner.ui.home.HomeScreen
import com.ilyk.cleaningplanner.ui.voice.ClaraVoiceScreen
import com.ilyk.cleaningplanner.ui.task.TaskDetailScreen
import com.ilyk.cleaningplanner.ui.family.FamilyModeScreen
import com.ilyk.cleaningplanner.ui.settings.SettingsScreen
import com.ilyk.cleaningplanner.feature.clara.chat.ClaraTextChatScreen

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
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    Box(modifier = modifier) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route
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
                        navController.popBackStack()
                    },
                    onOnboardingComplete = { result ->
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

