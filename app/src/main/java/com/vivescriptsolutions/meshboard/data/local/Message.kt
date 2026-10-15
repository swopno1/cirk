package com.vivescriptsolutions.meshboard.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Public bulletin announcement entity supporting multi-hop store-and-forward relay logic.
 * Includes fields for networkId, senderId, hopLimit, and timestamp.
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["networkId", "timestamp"]),
        Index(value = ["networkId", "createdAt"]),
        Index(value = ["expiresAt"])
    ]
)
data class Message(
    @PrimaryKey
    val messageId: String,
    val networkId: String,
    val senderId: String,
    val senderDisplayName: String,
    val senderRole: String,
    val messageType: String,
    val priority: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = timestamp,
    val receivedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = timestamp + (30 * 60 * 1000L),
    val hopLimit: Int,
    val currentHopCount: Int = 0,
    val text: String,
    val originGrant: String? = null,
    val deliveryStatus: String = "RECEIVED_FROM_NETWORK"
)
