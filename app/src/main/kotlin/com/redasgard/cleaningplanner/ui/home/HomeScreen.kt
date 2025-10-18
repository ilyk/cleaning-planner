package com.redasgard.cleaningplanner.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
) {
    data object Board : BottomNavItem("board", Icons.Default.Dashboard, "Board")
    data object Rooms : BottomNavItem("rooms", Icons.Default.Home, "Rooms")
    data object Settings : BottomNavItem("settings", Icons.Default.Settings, "Settings")
}

@Composable
fun HomeScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        BottomNavItem.Board,
        BottomNavItem.Rooms,
        BottomNavItem.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> BoardTabContent()
                1 -> RoomsTabContent()
                2 -> SettingsTabContent()
            }
        }
    }
}

@Composable
fun BoardTabContent() {
    Text(
        text = "Board - Coming soon",
        modifier = Modifier.padding(androidx.compose.ui.unit.dp(16.dp))
    )
}

@Composable
fun RoomsTabContent() {
    Text(
        text = "Rooms - Coming soon",
        modifier = Modifier.padding(androidx.compose.ui.unit.dp(16.dp))
    )
}

@Composable
fun SettingsTabContent() {
    Text(
        text = "Settings - Coming soon",
        modifier = Modifier.padding(androidx.compose.ui.unit.dp(16.dp))
    )
}

