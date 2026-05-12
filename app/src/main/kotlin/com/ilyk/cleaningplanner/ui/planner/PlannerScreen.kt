package com.ilyk.cleaningplanner.ui.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ilyk.cleaningplanner.domain.model.Task
import com.ilyk.cleaningplanner.domain.model.TaskPriority

private val Mint = Color(0xFF54C5B6)
private val MintSoft = Color(0x3354C5B6)
private val Amber = Color(0xFFFFE0B2)
private val Background = Color(0xFFF9FAFA)
private val Slate = Color(0xFF2D3748)
private val Muted = Color(0xFF6B7280)

@Composable
fun PlannerScreen(
    modifier: Modifier = Modifier,
    viewModel: PlannerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = Background,
        floatingActionButton = {
            if (uiState.selectedTab == PlannerTab.Custom) {
                ExtendedFloatingActionButton(
                    onClick = { showAddSheet = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add task") },
                    containerColor = Mint,
                    contentColor = Color.White
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Header(date = uiState.today.toString(), modeName = uiState.mode.name)

            TabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                containerColor = Background,
                contentColor = Mint
            ) {
                PlannerTab.values().forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.onTabChange(tab) },
                        text = { Text(tab.name, fontWeight = FontWeight.Medium) }
                    )
                }
            }

            when {
                uiState.isLoading -> LoadingState()
                uiState.error != null -> ErrorState(uiState.error!!)
                else -> when (uiState.selectedTab) {
                    PlannerTab.Daily -> DailyView(uiState.tasksByPriority, viewModel::onCompleteTask, viewModel::onSkipTask)
                    PlannerTab.Weekly -> WeeklyView()
                    PlannerTab.Seasonal -> ComingSoonView("Seasonal", "W6.2 follow-up — seasonal plan templates land alongside Paper Bridge (W9).")
                    PlannerTab.Custom -> CustomView(uiState.customTasks)
                }
            }
        }
    }

    if (showAddSheet) {
        AddCustomTaskSheet(
            rooms = uiState.plan?.tasks?.mapNotNull { it.roomId }?.distinct().orEmpty(),
            onDismiss = { showAddSheet = false },
            onSubmit = { title, room, minutes, priority ->
                viewModel.onAddCustomTask(title, room, minutes, priority)
            }
        )
    }
}

@Composable
private fun Header(date: String, modeName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Today's plan", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Slate)
            Text(date, fontSize = 13.sp, color = Muted)
        }
        Box(
            modifier = Modifier
                .background(MintSoft, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(modeName, fontWeight = FontWeight.SemiBold, color = Mint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Mint)
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = Muted, modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun DailyView(
    tasksByPriority: Map<TaskPriority, List<Task>>,
    onComplete: (String) -> Unit,
    onSkip: (String) -> Unit
) {
    val order = listOf(TaskPriority.NOW, TaskPriority.NEXT, TaskPriority.LATER)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        order.forEach { priority ->
            val tasks = tasksByPriority[priority].orEmpty()
            if (tasks.isNotEmpty()) {
                item {
                    Text(
                        text = priorityLabel(priority),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                items(items = tasks, key = { it.id }) { task ->
                    TaskCard(task = task, onComplete = onComplete, onSkip = onSkip)
                }
            }
        }
        if (tasksByPriority.values.all { it.isEmpty() }) {
            item {
                Text(
                    "No tasks for today — generate a plan from Home, or add a custom task in the Custom tab.",
                    color = Muted,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    }
}

private fun priorityLabel(priority: TaskPriority): String = when (priority) {
    TaskPriority.NOW -> "Now"
    TaskPriority.NEXT -> "Next up"
    TaskPriority.LATER -> "Later"
}

@Composable
private fun TaskCard(
    task: Task,
    onComplete: (String) -> Unit,
    onSkip: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.Medium, color = Slate)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Pill(text = "${task.estimatedDurationMinutes} min", color = MintSoft, textColor = Mint)
                    task.roomId?.let { Pill(text = it, color = Color(0x33FFE0B2), textColor = Color(0xFF8B5A00)) }
                }
            }
            IconButton(onClick = { onSkip(task.id) }) {
                Icon(Icons.Default.Close, contentDescription = "Skip", tint = Muted)
            }
            IconButton(onClick = { onComplete(task.id) }) {
                Icon(Icons.Default.Check, contentDescription = "Done", tint = Mint)
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 12.sp, color = textColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WeeklyView() {
    // W6.1 follow-up: real completion-percentage rollup once history sync (W4.7-adjacent) lands.
    val today = remember { java.time.LocalDate.now() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(7) { offset ->
            val date = today.plusDays(offset.toLong())
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(date.toString(), color = Slate, fontWeight = FontWeight.Medium)
                    Text("—%", color = Muted, fontSize = 12.sp)
                }
            }
        }
        item {
            Text(
                "Completion percentages land with the W6.1 history rollup.",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun ComingSoonView(title: String, note: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Slate)
            Spacer(Modifier.height(8.dp))
            Text(note, color = Muted)
        }
    }
}

@Composable
private fun CustomView(customTasks: List<Task>) {
    if (customTasks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("No custom tasks yet", fontWeight = FontWeight.SemiBold, color = Slate)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tap the Add task button to draft one. Server-side persistence for custom tasks lands when the backend exposes the endpoint.",
                    color = Muted
                )
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = customTasks, key = { it.id }) { task ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(task.title, fontWeight = FontWeight.Medium, color = Slate)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Pill("${task.estimatedDurationMinutes} min", MintSoft, Mint)
                        task.roomId?.let { Pill(it, Color(0x33FFE0B2), Color(0xFF8B5A00)) }
                        Pill(task.priority.name, Color(0xFFEDEDEF), Slate)
                    }
                }
            }
        }
    }
}
