package com.vivescriptsolutions.meshboard.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Node authorization and delegation grant entity.
 * Persists approved relay node permissions, delegation authority, and offline verifiable grants
 * to enable multi-hop relaying and continuous operation even when the original Host is offline.
 */
@Entity(
    tableName = "node_authorizations",
    indices = [
        Index(value = ["networkId", "role"])
    ]
)
data class NodeAuthorization(
    @PrimaryKey
    val nodeId: String,
    val networkId: String,
    val displayName: String,
    val role: String,
    val approvedByNodeId: String,
    val canDelegate: Boolean,
    val authGrant: String,
    val approvedAt: Long = System.currentTimeMillis()
)
