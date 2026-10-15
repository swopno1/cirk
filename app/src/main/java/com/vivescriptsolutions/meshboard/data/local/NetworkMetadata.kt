package com.vivescriptsolutions.meshboard.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Metadata representing a MeshBoard local network.
 * Stores membership role, host credentials, delegation policy, and activation status.
 */
@Entity(tableName = "network_metadata")
data class NetworkMetadata(
    @PrimaryKey
    val networkId: String,
    val networkName: String,
    val hostDisplayName: String,
    val hostNodeId: String,
    val description: String = "",
    val isHost: Boolean = false,
    val userRole: String = "CLIENT",
    val canDelegate: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val networkFingerprint: String? = null
)
