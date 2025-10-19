package com.ilyk.cleaningplanner.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ilyk.cleaningplanner.data.fake.RoomsFakeRepo
import com.ilyk.cleaningplanner.nav.Routes
import com.ilyk.cleaningplanner.ui.qr.QrScreen

data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
)

@Composable
fun HomeScreen(
    onNavigateToAISettings: () -> Unit = {}
) {
    val navController = rememberNavController()
    val tabs = listOf(
        BottomNavItem(Routes.BOARD, Icons.Default.Dashboard, "Board"),
        BottomNavItem(Routes.ROOMS, Icons.Default.Home, "Rooms"),
        BottomNavItem(Routes.SETTINGS, Icons.Default.Settings, "Settings")
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.BOARD,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Routes.BOARD) { BoardTabContent(onScanQr = { navController.navigate(Routes.QR) }) }
            composable(Routes.ROOMS) { RoomsTabContent(onScanQr = { navController.navigate(Routes.QR) }) }
            composable(Routes.SETTINGS) { SettingsTabContent(onNavigateToAISettings = onNavigateToAISettings) }
            composable(Routes.QR) { QrScreen(onClose = { navController.popBackStack() }) }
        }
    }
}

@Composable
fun BoardTabContent(onScanQr: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text("Board - Coming soon")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onScanQr) { Text("Scan QR") }
    }
}

@Composable
fun RoomsTabContent(onScanQr: () -> Unit) {
    val rooms = remember { RoomsFakeRepo.rooms }
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScanQr,
                text = { Text("Scan QR") },
                icon = { }
            )
        }
    ) { p ->
        LazyColumn(Modifier.padding(p)) {
            items(rooms) { room ->
                ListItem(
                    headlineContent = { Text(room.name) },
                    supportingContent = { Text("Slug: ${room.qrSlug}") },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { }) { Text("Open") }
                            TextButton(onClick = { }) { Text("QR") }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun SettingsTabContent(
    onNavigateToAISettings: () -> Unit = {}
) {
    Column(Modifier.padding(16.dp).fillMaxSize()) {
        Text(
            "Settings",
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        ListItem(
            headlineContent = { Text("AI Assistant") },
            supportingContent = { Text("Configure OpenAI (GPT-5)") },
            leadingContent = {
                Icon(
                    androidx.compose.material.icons.Icons.Default.Settings,
                    contentDescription = null
                )
            },
            modifier = Modifier.clickable { onNavigateToAISettings() }
        )
        
        HorizontalDivider()
        
        ListItem(
            headlineContent = { Text("Avatar Settings") },
            supportingContent = { Text("Customize Clara's appearance and voice") },
            leadingContent = {
                Icon(
                    androidx.compose.material.icons.Icons.Default.Face,
                    contentDescription = null
                )
            }
        )
        
        HorizontalDivider()
        
        ListItem(
            headlineContent = { Text("Account") },
            supportingContent = { Text("Coming soon") }
        )
    }
}

