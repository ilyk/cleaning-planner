package com.ilyk.cleaningplanner.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * CleanFlow bottom navigation bar.
 *
 * Five top-level destinations: Home / Planner / Family / Kids / AI.
 * Translucent surface (white at ~95% alpha) with the soft-mint primary on the active tab.
 *
 * Bottom-bar visibility is decided by the host NavHost — this composable only renders the bar
 * itself. Pass the current route in [currentRoute]; pass tab selection back via [onSelect].
 */
@Composable
fun CleanFlowBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.height(BAR_HEIGHT),
        containerColor = BAR_BG,
        tonalElevation = 0.dp
    ) {
        TABS.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { if (currentRoute != tab.route) onSelect(tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = {
                    Text(
                        text = tab.label,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ACTIVE_TINT,
                    selectedTextColor = ACTIVE_TINT,
                    indicatorColor = ACTIVE_INDICATOR,
                    unselectedIconColor = INACTIVE_TINT,
                    unselectedTextColor = INACTIVE_TINT
                )
            )
        }
    }
}

/** Reserved offset for any floating overlay (e.g. Clara avatar) sitting above the bar. */
val CleanFlowBottomBarOverlayOffset = 24.dp

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabSpec("home", "Home", Icons.Filled.Home),
    TabSpec("planner", "Planner", Icons.Filled.CalendarMonth),
    TabSpec("family", "Family", Icons.Filled.Group),
    TabSpec("kids", "Kids", Icons.Filled.ChildCare),
    TabSpec("ai", "AI", Icons.Filled.AutoAwesome),
)

/** Set of routes that should show the bottom bar. */
val CleanFlowTabRoutes: Set<String> = TABS.map { it.route }.toSet()

private val BAR_HEIGHT = 72.dp
private val BAR_BG = Color(0xF2FFFFFF) // ~95% alpha white
private val ACTIVE_TINT = Color(0xFF54C5B6) // soft-mint primary
private val ACTIVE_INDICATOR = Color(0x3354C5B6) // mint glow at 20% alpha
private val INACTIVE_TINT = Color(0xFF6B7280) // calm slate
