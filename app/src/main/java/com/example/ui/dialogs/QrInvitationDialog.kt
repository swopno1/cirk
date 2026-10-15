package com.example.ui.dialogs

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NetworkInfo
import com.example.data.model.NodeRole
import com.example.mesh.invitation.InvitationCapability
import com.example.mesh.invitation.MeshInvitation
import com.example.qr.QrCodeGenerator

@Composable
fun QrInvitationDialog(
    network: NetworkInfo,
    localNodeId: String,
    localDisplayName: String,
    canDelegate: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedCapability by remember { mutableStateOf(InvitationCapability.JOIN_CLIENT) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var invitationUri by remember { mutableStateOf("") }
    var copyNotice by remember { mutableStateOf(false) }

    LaunchedEffect(selectedCapability, network) {
        val inv = MeshInvitation.create(
            networkId = network.networkId,
            networkName = network.networkName,
            inviterNodeId = localNodeId,
            inviterDisplayName = localDisplayName,
            capability = selectedCapability
        )
        val uri = inv.toInvitationUriString()
        invitationUri = uri
        qrBitmap = QrCodeGenerator.generateQrBitmap(uri, widthPx = 512, heightPx = 512)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Invite to ${network.networkName}",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Offline QR & Token Invitation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Capability Selector
                Text(
                    text = "Grant Access Level:",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = selectedCapability == InvitationCapability.JOIN_CLIENT,
                        onClick = { selectedCapability = InvitationCapability.JOIN_CLIENT },
                        label = { Text("Client", fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("invite_client_chip")
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    FilterChip(
                        selected = selectedCapability == InvitationCapability.JOIN_RELAY_NODE,
                        onClick = { selectedCapability = InvitationCapability.JOIN_RELAY_NODE },
                        label = { Text("Relay", fontSize = 12.sp) },
                        enabled = network.isHost || canDelegate,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("invite_relay_chip")
                    )

                    if (network.isHost || canDelegate) {
                        Spacer(modifier = Modifier.width(4.dp))
                        FilterChip(
                            selected = selectedCapability == InvitationCapability.JOIN_RELAY_NODE_AND_DELEGATE,
                            onClick = { selectedCapability = InvitationCapability.JOIN_RELAY_NODE_AND_DELEGATE },
                            label = { Text("+Delegate", fontSize = 11.sp) },
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("invite_delegate_chip")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // QR Code Image
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    qrBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "CIRK QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } ?: CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Copy code section
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = android.content.ClipData.newPlainText("CIRK Invitation", invitationUri)
                        cm.setPrimaryClip(clip)
                        copyNotice = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("copy_invitation_code_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy code")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (copyNotice) "Copied to Clipboard!" else "Copy Invitation Code")
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Joining devices can scan this QR offline or enter the invitation code manually.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
