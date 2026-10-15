package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local user profile stored on device.
 * No cloud server, email, or phone number required.
 */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey
    val profileId: String,
    val displayName: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Roles a user can hold in a MeshBoard network.
 */
enum class NodeRole {
    HOST,
    RELAY_NODE,
    CLIENT;

    companion object {
        fun fromString(value: String): NodeRole {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CLIENT
        }
    }
}

/**
 * Active or joined network metadata.
 */
@Entity(tableName = "network_info")
data class NetworkInfo(
    @PrimaryKey
    val networkId: String,
    val networkName: String,
    val hostDisplayName: String,
    val hostNodeId: String,
    val description: String = "",
    val isHost: Boolean = false,
    val userRole: NodeRole = NodeRole.CLIENT,
    val canDelegate: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

/**
 * Status of public bulletin message on this device.
 */
enum class MessageDeliveryStatus {
    CREATED_LOCALLY,
    QUEUED_FOR_FORWARDING,
    RECEIVED_FROM_NETWORK,
    FORWARDED_TO_PEERS
}

/**
 * Public bulletin message persisted locally in Room.
 */
@Entity(tableName = "mesh_messages")
data class MeshMessageEntity(
    @PrimaryKey
    val messageId: String,
    val networkId: String,
    val senderId: String,
    val senderDisplayName: String,
    val senderRole: NodeRole,
    val messageType: String,
    val priority: Int,
    val createdAt: Long,
    val receivedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long,
    val hopLimit: Int,
    val currentHopCount: Int,
    val text: String,
    val originGrant: String? = null,
    val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.RECEIVED_FROM_NETWORK
)

/**
 * Bounded store-and-forward queue persisted locally.
 */
@Entity(tableName = "pending_queue")
data class PendingQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val messageId: String,
    val networkId: String,
    val priority: Int,
    val payloadJson: String,
    val createdAt: Long,
    val expiresAt: Long,
    val targetPeerId: String? = null,
    val attempts: Int = 0
)

/**
 * Duplicate suppression cache entries in Room.
 */
@Entity(tableName = "seen_messages")
data class SeenMessageEntity(
    @PrimaryKey
    val messageId: String,
    val networkId: String,
    val firstSeenAt: Long = System.currentTimeMillis()
)

/**
 * Approved node delegation records.
 */
@Entity(tableName = "node_authorizations")
data class NodeAuthorizationEntity(
    @PrimaryKey
    val nodeId: String,
    val networkId: String,
    val displayName: String,
    val role: NodeRole,
    val approvedByNodeId: String,
    val canDelegate: Boolean,
    val authGrant: String,
    val approvedAt: Long = System.currentTimeMillis()
)
