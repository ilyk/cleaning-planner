package com.ilyk.cleaningplanner.feature.setup.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ilyk.cleaningplanner.feature.setup.PetType
import com.ilyk.cleaningplanner.feature.setup.SetupPet
import com.ilyk.cleaningplanner.feature.setup.SetupRoom

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetsStep(
    pets: List<SetupPet>,
    rooms: List<SetupRoom>,
    onAddPet: (name: String, type: PetType, sheddingLevel: Int) -> Unit,
    onUpdatePet: (petId: String, name: String, type: PetType, sheddingLevel: Int) -> Unit,
    onRemovePet: (petId: String) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    var newPetName by remember { mutableStateOf("") }
    var newPetType by remember { mutableStateOf(PetType.Dog) }
    var newSheddingLevel by remember { mutableFloatStateOf(1f) }
    var typeDropdownExpanded by remember { mutableStateOf(false) }

    val sheddingLabels = listOf("None", "Low", "Medium", "High")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Do you have pets?",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Adding pets helps CleanFlow suggest pet-specific cleaning tasks like vacuuming fur or cleaning pet areas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Add pet form
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                OutlinedTextField(
                    value = newPetName,
                    onValueChange = { newPetName = it },
                    label = { Text("Pet Name") },
                    placeholder = { Text("e.g., Buddy") },
                    leadingIcon = { Icon(Icons.Default.Pets, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = typeDropdownExpanded,
                    onExpandedChange = { typeDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = newPetType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Pet Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = typeDropdownExpanded,
                        onDismissRequest = { typeDropdownExpanded = false }
                    ) {
                        PetType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    newPetType = type
                                    typeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Shedding Level: ${sheddingLabels[newSheddingLevel.toInt()]}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Slider(
                    value = newSheddingLevel,
                    onValueChange = { newSheddingLevel = it },
                    valueRange = 0f..3f,
                    steps = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        if (newPetName.isNotBlank()) {
                            onAddPet(newPetName, newPetType, newSheddingLevel.toInt())
                            newPetName = ""
                            newSheddingLevel = 1f
                        }
                    },
                    enabled = newPetName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Pet")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pet list
        if (pets.isNotEmpty()) {
            Text(
                text = "Your Pets (${pets.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pets, key = { it.id }) { pet ->
                PetItem(
                    pet = pet,
                    sheddingLabels = sheddingLabels,
                    onRemove = { onRemovePet(pet.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f)
            ) {
                Text("No Pets")
            }

            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f)
            ) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun PetItem(
    pet: SetupPet,
    sheddingLabels: List<String>,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.Pets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = pet.name,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${pet.type.displayName} - Shedding: ${sheddingLabels.getOrElse(pet.sheddingLevel) { "Unknown" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove pet",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
