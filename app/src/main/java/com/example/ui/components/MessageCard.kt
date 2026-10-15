package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MessageDeliveryStatus
import com.example.data.model.MeshMessageEntity
import com.example.data.model.NodeRole
import com.example.ui.theme.CirkClientPink
import com.example.ui.theme.CirkHostGold
import com.example.ui.theme.CirkRelayBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageCard(
    message: MeshMessageEntity,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(message.createdAt))

    val borderColor = when (message.senderRole) {
        NodeRole.HOST -> CirkHostGold.copy(alpha = 0.5f)
        NodeRole.RELAY_NODE -> CirkRelayBlue.copy(alpha = 0.4f)
        NodeRole.CLIENT -> CirkClientPink.copy(alpha = 0.25f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("message_card_${message.messageId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Sender name, Role badge, Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.senderDisplayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                RoleBadge(role = message.senderRole)

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body text
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Delivery / Relay telemetry
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (statusIcon, statusText) = when (message.deliveryStatus) {
                    MessageDeliveryStatus.CREATED_LOCALLY -> Pair(
                        Icons.Default.Check,
                        "Created locally"
                    )
                    MessageDeliveryStatus.QUEUED_FOR_FORWARDING -> Pair(
                        Icons.Default.Schedule,
                        "Queued for forwarding"
                    )
                    MessageDeliveryStatus.FORWARDED_TO_PEERS -> Pair(
                        Icons.Default.DoneAll,
                        "Forwarded to mesh peers"
                    )
                    MessageDeliveryStatus.RECEIVED_FROM_NETWORK -> Pair(
                        Icons.Default.Check,
                        if (message.currentHopCount > 0) "Relayed via ${message.currentHopCount} hops" else "Direct peer delivery"
                    )
                }

                Icon(
                    imageVector = statusIcon,
                    contentDescription = statusText,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(13.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.weight(1f))

                if (message.senderRole == NodeRole.CLIENT) {
                    Text(
                        text = "Max 3 hops",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
