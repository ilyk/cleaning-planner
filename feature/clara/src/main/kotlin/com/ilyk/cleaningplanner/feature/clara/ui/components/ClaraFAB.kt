package com.ilyk.cleaningplanner.feature.clara.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ilyk.cleaningplanner.core.model.AvatarAppearance
import com.ilyk.cleaningplanner.core.model.AvatarPrefs

@Composable
fun ClaraFAB(
    avatarPrefs: AvatarPrefs,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = "Clara avatar settings"
        },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        BadgedBox(
            badge = {
                if (avatarPrefs.muteVoice) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = "Muted",
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        ) {
            val appearance = AvatarAppearance.fromId(avatarPrefs.appearanceId)
            ClaraAvatar(
                appearance = appearance,
                size = 32.dp
            )
        }
    }
}

