package com.example.medvoiceafrica

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MedicationsDialog(
    medications: List<String>,
    onAddMedication: (String) -> Unit,
    onRemoveMedication: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newMedicationText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Default.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Traitements en cours") 
            }
        },
        text = {
            Column {
                Text(
                    text = "Ajoutez vos médicaments actuels pour que l'assistant médical en tienne compte dans ses analyses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = newMedicationText,
                    onValueChange = { newMedicationText = it },
                    label = { Text("Nouveau médicament") },
                    placeholder = { Text("Ex: Paracétamol, Insuline...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (newMedicationText.isNotBlank()) {
                                onAddMedication(newMedicationText.trim())
                                newMedicationText = ""
                            }
                        }
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (newMedicationText.isNotBlank()) {
                                    onAddMedication(newMedicationText.trim())
                                    newMedicationText = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Ajouter")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // FlowRow permet aux "chips" de passer à la ligne automatiquement
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    medications.forEach { med ->
                        InputChip(
                            selected = false,
                            onClick = { },
                            label = { Text(med) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Supprimer $med",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onRemoveMedication(med) }
                                )
                            },
                            // S'assurer que le chip est bien visible sur fond blanc/gris
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Terminer")
            }
        }
    )
}