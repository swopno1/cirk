package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NodeRole
import com.example.ui.theme.CirkClientPink
import com.example.ui.theme.CirkHostGold
import com.example.ui.theme.CirkRelayBlue

@Composable
fun RoleBadge(
    role: NodeRole,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label, icon) = when (role) {
        NodeRole.HOST -> Quadruple(
            CirkHostGold.copy(alpha = 0.2f),
            CirkHostGold,
            "HOST",
            Icons.Default.Star
        )
        NodeRole.RELAY_NODE -> Quadruple(
            CirkRelayBlue.copy(alpha = 0.2f),
            CirkRelayBlue,
            "RELAY",
            Icons.Default.CellTower
        )
        NodeRole.CLIENT -> Quadruple(
            CirkClientPink.copy(alpha = 0.18f),
            CirkClientPink,
            "MEMBER",
            Icons.Default.Person
        )
    }

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = textColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
