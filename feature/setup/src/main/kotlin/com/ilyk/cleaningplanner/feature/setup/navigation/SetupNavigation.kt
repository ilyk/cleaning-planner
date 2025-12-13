package com.ilyk.cleaningplanner.feature.setup.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.ilyk.cleaningplanner.feature.setup.screens.SetupWizardScreen

/**
 * Route for the setup wizard
 */
const val SETUP_WIZARD_ROUTE = "setup_wizard"

/**
 * Navigate to the setup wizard
 */
fun NavController.navigateToSetupWizard(navOptions: NavOptions? = null) {
    navigate(SETUP_WIZARD_ROUTE, navOptions)
}

/**
 * Add setup wizard screen to nav graph
 */
fun NavGraphBuilder.setupWizardScreen(
    onSetupComplete: (homeId: String) -> Unit,
    onBack: () -> Unit
) {
    composable(route = SETUP_WIZARD_ROUTE) {
        SetupWizardScreen(
            onSetupComplete = onSetupComplete,
            onBack = onBack
        )
    }
}
