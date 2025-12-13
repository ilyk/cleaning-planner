package com.ilyk.cleaningplanner.feature.setup.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ilyk.cleaningplanner.feature.setup.SetupEvent
import com.ilyk.cleaningplanner.feature.setup.SetupStep
import com.ilyk.cleaningplanner.feature.setup.SetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    onSetupComplete: (homeId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle one-time events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SetupEvent.NavigateToHome -> onSetupComplete(event.homeId)
                is SetupEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // Show error in snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val stepTitles = mapOf(
        SetupStep.HOME_NAME to "Name Your Home",
        SetupStep.ROOMS to "Add Rooms",
        SetupStep.MEMBERS to "Household Members",
        SetupStep.PETS to "Pets (Optional)",
        SetupStep.PREFERENCES to "Preferences (Optional)",
        SetupStep.REVIEW to "Review & Finish"
    )

    val progress = when (uiState.currentStep) {
        SetupStep.HOME_NAME -> 0.17f
        SetupStep.ROOMS -> 0.33f
        SetupStep.MEMBERS -> 0.50f
        SetupStep.PETS -> 0.67f
        SetupStep.PREFERENCES -> 0.83f
        SetupStep.REVIEW -> 1.0f
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stepTitles[uiState.currentStep] ?: "Setup") },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (uiState.currentStep == SetupStep.HOME_NAME) {
                                onBack()
                            } else {
                                viewModel.previousStep()
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        AnimatedContent(
            targetState = uiState.currentStep,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn()).togetherWith(
                    slideOutHorizontally { -it } + fadeOut()
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            label = "SetupStep"
        ) { step ->
            when (step) {
                SetupStep.HOME_NAME -> HomeNameStep(
                    homeName = uiState.homeName,
                    onHomeNameChange = viewModel::setHomeName,
                    onNext = { viewModel.nextStep() },
                    canProceed = viewModel.canProceed()
                )
                SetupStep.ROOMS -> RoomsStep(
                    rooms = uiState.rooms,
                    onAddRoom = viewModel::addRoom,
                    onUpdateRoom = viewModel::updateRoom,
                    onRemoveRoom = viewModel::removeRoom,
                    onNext = { viewModel.nextStep() },
                    canProceed = viewModel.canProceed()
                )
                SetupStep.MEMBERS -> MembersStep(
                    members = uiState.members,
                    onAddMember = viewModel::addMember,
                    onUpdateMember = viewModel::updateMember,
                    onRemoveMember = viewModel::removeMember,
                    onNext = { viewModel.nextStep() },
                    onSkip = { viewModel.nextStep() }
                )
                SetupStep.PETS -> PetsStep(
                    pets = uiState.pets,
                    rooms = uiState.rooms,
                    onAddPet = viewModel::addPet,
                    onUpdatePet = viewModel::updatePet,
                    onRemovePet = viewModel::removePet,
                    onNext = { viewModel.nextStep() },
                    onSkip = { viewModel.skipPetsStep() }
                )
                SetupStep.PREFERENCES -> PreferencesStep(
                    preferences = uiState.preferences,
                    onUpdatePreferences = viewModel::updatePreferences,
                    onNext = { viewModel.nextStep() },
                    onSkip = { viewModel.skipPreferencesStep() }
                )
                SetupStep.REVIEW -> ReviewStep(
                    homeName = uiState.homeName,
                    rooms = uiState.rooms,
                    members = uiState.members,
                    pets = uiState.pets,
                    preferences = uiState.preferences,
                    isLoading = uiState.isLoading,
                    onSubmit = { viewModel.submitSetup() },
                    onEditRooms = { viewModel.previousStep(); viewModel.previousStep(); viewModel.previousStep(); viewModel.previousStep() }
                )
            }
        }
    }
}
