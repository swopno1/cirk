package com.example.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NetworkInfo
import com.example.data.model.UserProfile

@Composable
fun SettingsDialog(
    profile: UserProfile?,
    network: NetworkInfo?,
    isBatterySaver: Boolean,
    onUpdateName: (String) -> Unit,
    onToggleBattery: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onLeaveNetwork: () -> Unit,
    onShutdownNetwork: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var editedName by remember { mutableStateOf(profile?.displayName ?: "") }
    var showShutdownConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings & Network Options") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Section 1: Display Name
                Text(
                    text = "PROFILE IDENTITY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Display Name") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_name_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onUpdateName(editedName) },
                        enabled = editedName.isNotBlank() && editedName != profile?.displayName
                    ) {
                        Text("Save")
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Section 2: Battery Conscious Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.BatterySaver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Battery-Conscious Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Reduces discovery frequency while preserving existing relay connections",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isBatterySaver,
                        onCheckedChange = { onToggleBattery(it) },
                        modifier = Modifier.testTag("settings_battery_switch")
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Section 3: History & Network Actions
                if (network != null) {
                    Text(
                        text = "NETWORK CONTROLS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = onClearHistory,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Local Message History")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onLeaveNetwork,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_leave_network_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Leave Network")
                    }

                    if (network.isHost) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showShutdownConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Broadcast Network Shutdown (60s)")
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Section 4: About & Legal
                Text(
                    text = "ABOUT & PRIVACY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "CIRK — Offline Mesh Network",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "pronounced “sirk” • Your people. Your network. Anywhere.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "CIRK Connect v1.0.0 • Protocol v1",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "© 2026 ViveScript Solutions LLC.\nDeveloper: ViveScript Solutions LLC\nWebsite: https://www.vivescriptsolutions.com/",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Zero Cloud Tracking: CIRK operates entirely peer-to-peer over local Android radios. No internet connection, server, account, or personal data tracking is used. Public announcements and bulletins are broadcasted to authorized local network participants.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )

    if (showShutdownConfirm) {
        AlertDialog(
            onDismissRequest = { showShutdownConfirm = false },
            title = { Text("Confirm Network Shutdown") },
            text = {
                Text("This will broadcast a high-priority shutdown notice to all connected nodes with a 60-second countdown and close this network session.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showShutdownConfirm = false
                        onShutdownNetwork(60)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Shutdown Network")
                }
            },
            dismissButton = {
                TextButton(onClick = { showShutdownConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
