package com.ilyk.cleaningplanner.feature.clara.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ilyk.cleaningplanner.core.model.AvatarAppearance

@Composable
fun ClaraAvatar(
    appearance: AvatarAppearance,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = getAvatarIcon(appearance),
            contentDescription = "${appearance.displayName} avatar",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(size * 0.6f)
        )
    }
}

private fun getAvatarIcon(appearance: AvatarAppearance): ImageVector {
    return when (appearance) {
        AvatarAppearance.CLARA, AvatarAppearance.AYA -> Icons.Filled.Face
        AvatarAppearance.LEO, AvatarAppearance.MAX -> Icons.Filled.Person
        AvatarAppearance.SAM, AvatarAppearance.REN -> Icons.Outlined.Face
    }
}

