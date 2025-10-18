package com.ilyk.cleaningplanner.feature.clara.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ilyk.cleaningplanner.core.model.AvatarAppearance
import com.ilyk.cleaningplanner.core.model.AvatarCategory
import com.ilyk.cleaningplanner.core.model.AvatarPrefs
import com.ilyk.cleaningplanner.core.model.VoiceStyle
import com.ilyk.cleaningplanner.feature.clara.ui.components.ClaraAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarSettingsSheet(
    currentPrefs: AvatarPrefs,
    onDismiss: () -> Unit,
    onSave: (AvatarPrefs) -> Unit,
    modifier: Modifier = Modifier
) {
    var prefs by remember { mutableStateOf(currentPrefs) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Avatar Settings",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            AvatarCategory.entries.forEach { category ->
                val avatars = AvatarAppearance.entries.filter { it.category == category }
                
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    items(avatars) { avatar ->
                        AvatarOption(
                            avatar = avatar,
                            isSelected = prefs.appearanceId == avatar.id,
                            onClick = {
                                prefs = prefs.copy(appearanceId = avatar.id)
                            }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text(
                text = "Voice Style",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                VoiceStyle.entries.forEach { voice ->
                    FilterChip(
                        selected = prefs.voiceId == voice.id,
                        onClick = { prefs = prefs.copy(voiceId = voice.id) },
                        label = { Text(voice.displayName) },
                        leadingIcon = if (prefs.voiceId == voice.id) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SettingSwitch(
                label = "Show avatar",
                checked = prefs.showAvatar,
                onCheckedChange = { prefs = prefs.copy(showAvatar = it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingSwitch(
                label = "Mute voice",
                checked = prefs.muteVoice,
                onCheckedChange = { prefs = prefs.copy(muteVoice = it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingSwitch(
                label = "Always show subtitles",
                checked = prefs.alwaysShowSubtitles,
                onCheckedChange = { prefs = prefs.copy(alwaysShowSubtitles = it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { onSave(prefs) }) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun AvatarOption(
    avatar: AvatarAppearance,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
            .semantics {
                contentDescription = "${avatar.displayName}, ${if (isSelected) "selected" else "not selected"}"
            }
    ) {
        ClaraAvatar(
            appearance = avatar,
            size = 64.dp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = avatar.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics {
                contentDescription = "$label: ${if (checked) "on" else "off"}"
            }
        )
    }
}

