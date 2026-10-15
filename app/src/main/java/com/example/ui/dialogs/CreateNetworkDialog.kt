package com.example.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun CreateNetworkDialog(
    initialHostName: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, hostAlias: String, description: String, canDelegate: Boolean) -> Unit
) {
    var networkName by remember { mutableStateOf("") }
    var hostAlias by remember { mutableStateOf(initialHostName) }
    var description by remember { mutableStateOf("") }
    var canDelegate by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create CIRK Network") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = networkName,
                    onValueChange = { networkName = it },
                    label = { Text("Network Name *") },
                    placeholder = { Text("e.g. Campus Circle, Festival Camp 4") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_network_name_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = hostAlias,
                    onValueChange = { hostAlias = it },
                    label = { Text("Host Display Name *") },
                    placeholder = { Text("e.g. Organizer Amir") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_network_host_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Network Description (Optional)") },
                    placeholder = { Text("e.g. Public emergency information channel") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Allow Node Delegation",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Approved Relay Nodes can invite more Relay Nodes to extend coverage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = canDelegate,
                        onCheckedChange = { canDelegate = it },
                        modifier = Modifier.testTag("create_network_delegate_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (networkName.isNotBlank() && hostAlias.isNotBlank()) {
                        onCreate(networkName, hostAlias, description, canDelegate)
                    }
                },
                enabled = networkName.isNotBlank() && hostAlias.isNotBlank(),
                modifier = Modifier.testTag("create_network_confirm")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
