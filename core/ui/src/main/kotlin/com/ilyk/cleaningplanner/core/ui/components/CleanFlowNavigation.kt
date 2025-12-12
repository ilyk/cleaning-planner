package com.ilyk.cleaningplanner.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.systemBarsPadding
import com.ilyk.cleaningplanner.core.ui.theme.*

/**
 * CleanFlow Floating Bottom Navigation v3
 * Floating above translucent blur with soft teal glow for active tab
 */

@Composable
fun CleanFlowBottomNavigation(
    selectedTab: CleanFlowTab,
    onTabSelected: (CleanFlowTab) -> Unit,
    modifier: Modifier = Modifier
) {
    // Floating translucent background
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp), // No system bars padding
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = NavigationBackground // Translucent blur background
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CleanFlowTab.values().forEach { tab ->
                CleanFlowTabItem(
                    tab = tab,
                    isSelected = selectedTab == tab,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}

@Composable
fun CleanFlowTabItem(
    tab: CleanFlowTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Soft teal glow behind active tab
    val glowScale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = tween(300, easing = androidx.compose.animation.core.EaseOutCubic),
        label = "glow_scale"
    )
    
    val glowAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.2f else 0f,
        animationSpec = tween(300),
        label = "glow_alpha"
    )

    Box(
        modifier = modifier
            .clickable { onClick() }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Soft glow background for active tab
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .scale(glowScale)
                    .background(
                        color = ActiveTabGlow.copy(alpha = glowAlpha),
                        shape = CircleShape
                    )
            )
        }
        
        // Tab content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.title,
                modifier = Modifier.size(24.dp),
                tint = if (isSelected) TealAccent else TextSecondary
            )
            
            Text(
                text = tab.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) TealAccent else TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

enum class CleanFlowTab(
    val title: String,
    val icon: ImageVector
) {
    HOME("Home", Icons.Default.Home),
    PLANNER("Planner", Icons.Default.CalendarToday),
    FAMILY("Family", Icons.Default.Group),
    KIDS("Kids", Icons.Default.ChildCare),
    AI("AI", Icons.Default.Psychology)
}
