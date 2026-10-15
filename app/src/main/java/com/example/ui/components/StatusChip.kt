package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mesh.routing.NetworkMeshStatus
import com.example.ui.theme.CirkErrorRed
import com.example.ui.theme.CirkHostGold
import com.example.ui.theme.CirkRelayBlue
import com.example.ui.theme.CirkSuccessGreen
import com.example.ui.theme.CirkWarningOrange

@Composable
fun StatusChip(
    status: NetworkMeshStatus,
    modifier: Modifier = Modifier
) {
    val (dotColor, text) = when (status) {
        NetworkMeshStatus.HOST_ACTIVE -> Pair(CirkHostGold, "HOST ACTIVE")
        NetworkMeshStatus.RELAY_CONNECTED -> Pair(CirkSuccessGreen, "CIRK RELAY ACTIVE")
        NetworkMeshStatus.HOST_OFFLINE_MESH_CONTINUING -> Pair(CirkWarningOrange, "HOST OFFLINE — CIRK CONTINUING")
        NetworkMeshStatus.CLIENT_CONNECTED -> Pair(CirkRelayBlue, "CONNECTED")
        NetworkMeshStatus.SHUTTING_DOWN -> Pair(CirkErrorRed, "SHUTTING DOWN")
        NetworkMeshStatus.DISCONNECTED -> Pair(Color(0xFF94A3B8), "OFFLINE")
    }

    Box(
        modifier = modifier
            .background(dotColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(dotColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = dotColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp
            )
        }
    }
}
