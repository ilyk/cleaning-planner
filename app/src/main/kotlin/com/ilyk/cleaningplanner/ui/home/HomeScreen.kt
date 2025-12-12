package com.ilyk.cleaningplanner.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dagger.hilt.android.EntryPointAccessors
import com.ilyk.cleaningplanner.R
import com.ilyk.cleaningplanner.state.PrefsStoreEntryPoint
import com.ilyk.cleaningplanner.domain.model.CleaningMode
import com.ilyk.cleaningplanner.domain.model.Task
import com.ilyk.cleaningplanner.state.PrefsStore
import com.ilyk.cleaningplanner.domain.model.TaskPriority

/**
 * Home screen showing today's cleaning plan
 * Implements states: loading → cached+updating → ready → error-with-cache
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToVoice: () -> Unit = {},
    onNavigateToTextChat: () -> Unit = {},
    onNavigateToTaskDetail: (String) -> Unit = {},
    onNavigateToFamily: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Inject PrefsStore via Hilt EntryPoint
    val prefsStore = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PrefsStoreEntryPoint::class.java
        ).prefsStore()
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with mode selector
        HomeHeader(
            currentMode = uiState.currentMode,
            onModeChanged = viewModel::changeMode,
            onRefresh = viewModel::refreshPlan,
            onOptimize = viewModel::optimizePlan,
            isOptimizing = uiState.isOptimizing,
            optimizeError = uiState.optimizeError,
            onNavigateToSettings = onNavigateToSettings
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        when {
            uiState.isLoading && uiState.plan == null -> {
                // Initial loading state
                LoadingState()
            }
            
            uiState.error != null && uiState.plan == null -> {
                // Error state without cache
                ErrorState(
                    error = uiState.error ?: "Unknown error",
                    onRetry = viewModel::refreshPlan
                )
            }
            
            uiState.isLoading && uiState.plan != null -> {
                // Cached + updating state
                val plan = uiState.plan!!
                CachedUpdatingState(plan = plan, prefsStore = prefsStore)
            }
            
            uiState.error != null && uiState.plan != null -> {
                // Error with cache state
                val plan = uiState.plan!!
                val error = uiState.error
                ErrorWithCacheState(
                    plan = plan,
                    error = error ?: "Unknown error",
                    onRetry = viewModel::refreshPlan,
                    prefsStore = prefsStore
                )
            }
            
            uiState.plan != null -> {
                // Ready state
                val plan = uiState.plan!!
                ReadyState(
                    plan = plan,
                    onTaskComplete = viewModel::completeTask,
                    onTaskSkip = viewModel::skipTask,
                    onTaskAssign = viewModel::assignTask,
                    onNavigateToVoice = onNavigateToVoice,
                    onNavigateToTaskDetail = onNavigateToTaskDetail,
                    onNavigateToFamily = onNavigateToFamily,
                    prefsStore = prefsStore
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    currentMode: CleaningMode,
    onModeChanged: (CleaningMode) -> Unit,
    onRefresh: () -> Unit,
    onOptimize: () -> Unit,
    isOptimizing: Boolean,
    optimizeError: String?,
    onNavigateToSettings: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's Plan",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isOptimizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    TextButton(onClick = onOptimize) {
                        Text("Optimize", style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh"
                    )
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings"
                    )
                }
            }
        }
        
        // Show optimization error if any
        optimizeError?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Loading today's plan...")
        }
    }
}

@Composable
private fun ErrorState(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Error: $error")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun CachedUpdatingState(plan: com.ilyk.cleaningplanner.domain.model.Plan, prefsStore: PrefsStore) {
    Column {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Updating plan...")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        ReadyState(
            plan = plan,
            onTaskComplete = {},
            onTaskSkip = {},
            onTaskAssign = { _, _ -> },
            onNavigateToVoice = {},
            onNavigateToTaskDetail = {},
            onNavigateToFamily = {},
            prefsStore = prefsStore
        )
    }
}

@Composable
private fun ErrorWithCacheState(
    plan: com.ilyk.cleaningplanner.domain.model.Plan,
    error: String,
    onRetry: () -> Unit,
    prefsStore: PrefsStore
) {
    Column {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Error: $error")
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        ReadyState(
            plan = plan,
            onTaskComplete = {},
            onTaskSkip = {},
            onTaskAssign = { _, _ -> },
            onNavigateToVoice = {},
            onNavigateToTaskDetail = {},
            onNavigateToFamily = {},
            prefsStore = prefsStore
        )
    }
}

@Composable
private fun ReadyState(
    plan: com.ilyk.cleaningplanner.domain.model.Plan,
    onTaskComplete: (String) -> Unit,
    onTaskSkip: (String) -> Unit,
    onTaskAssign: (String, String) -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    onNavigateToFamily: () -> Unit,
    prefsStore: PrefsStore
) {
    LazyColumn {
        item {
            // Quick actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Voice button with feature flag
                val claraEnabled by prefsStore.isClaraEnabled.collectAsState(false)
                if (claraEnabled) {
                    Button(
                        onClick = onNavigateToVoice,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Voice Assistant")
                    }
                } else {
                    OutlinedButton(
                        onClick = { /* TODO: Show disabled tooltip */ },
                        modifier = Modifier.weight(1f),
                        enabled = false
                    ) {
                        Text("Voice Assistant (Disabled)")
                    }
                }
                Button(
                    onClick = onNavigateToFamily,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Family")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        items(plan.tasks) { task ->
            TaskCard(
                task = task,
                onComplete = { onTaskComplete(task.id) },
                onSkip = { onTaskSkip(task.id) },
                onAssign = { memberId -> onTaskAssign(task.id, memberId) },
                onClick = { onNavigateToTaskDetail(task.id) }
            )
        }
    }
}

@Composable
private fun TaskCard(
    task: Task,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onAssign: (String) -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium
                )
                
                Text(
                    text = "${task.estimatedDurationMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (!task.description.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = task.description ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onComplete,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Done")
                }
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Skip")
                }
            }
        }
    }
}
