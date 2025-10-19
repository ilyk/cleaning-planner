package com.ilyk.cleaningplanner.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ilyk.cleaningplanner.feature.clara.ui.diagnostics.PerformanceDiagnosticsScreen
import com.ilyk.cleaningplanner.feature.clara.ui.intake.ChatIntakeScreen
import com.ilyk.cleaningplanner.feature.clara.ui.intake.TypeIntakeScreen
import com.ilyk.cleaningplanner.feature.clara.ui.settings.AIAssistantSettingsScreen
import com.ilyk.cleaningplanner.feature.clara.ui.settings.Avatar3DSettingsScreen
import com.ilyk.cleaningplanner.feature.clara.ui.setup.APIKeySetupScreen
import com.ilyk.cleaningplanner.feature.clara.ui.welcome.WelcomeScreen
import com.ilyk.cleaningplanner.feature.clara.ui.wizard.WizardScreen
import com.ilyk.cleaningplanner.ui.home.HomeScreen
import com.ilyk.cleaningplanner.ui.splash.SplashScreen

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object APIKeySetup : Screen("api_key_setup")
    data object Welcome : Screen("welcome")
    data object ChatIntake : Screen("chat_intake")
    data object TypeIntake : Screen("type_intake")
    data object Wizard : Screen("wizard")
    data object AISettings : Screen("ai_settings")
    data object Avatar3DSettings : Screen("avatar_3d_settings")
    data object Diagnostics : Screen("diagnostics")
    data object Home : Screen("home")
    data object Auth : Screen("auth")
    data object HouseholdSelect : Screen("household_select")
}

@Composable
fun CleaningPlannerNavHost(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToAuth = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.APIKeySetup.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.APIKeySetup.route) {
            APIKeySetupScreen(
                onConfigured = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(Screen.APIKeySetup.route) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(Screen.APIKeySetup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onNavigateToChat = {
                    navController.navigate(Screen.ChatIntake.route)
                },
                onNavigateToTypeInfo = {
                    navController.navigate(Screen.TypeIntake.route)
                },
                onNavigateToWizard = {
                    navController.navigate(Screen.Wizard.route)
                },
                onNavigateToAISettings = {
                    navController.navigate(Screen.AISettings.route)
                }
            )
        }

        composable(Screen.ChatIntake.route) {
            ChatIntakeScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.TypeIntake.route) {
            TypeIntakeScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Wizard.route) {
            WizardScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.AISettings.route) {
            AIAssistantSettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Avatar3DSettings.route) {
            Avatar3DSettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDiagnostics = {
                    navController.navigate(Screen.Diagnostics.route)
                }
            )
        }

        composable(Screen.Diagnostics.route) {
            PerformanceDiagnosticsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Auth.route) {
            SplashScreen(
                onNavigateToAuth = {},
                onNavigateToHome = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToAISettings = {
                    navController.navigate(Screen.AISettings.route)
                }
            )
        }
    }
}

