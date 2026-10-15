package com.example.mesh.transport

import com.example.data.model.NodeRole
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class PeerConnection(
    val endpointId: String,
    val nodeId: String,
    val displayName: String,
    val role: NodeRole,
    val connectedAt: Long = System.currentTimeMillis()
)

data class IncomingPacketEvent(
    val fromEndpointId: String,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IncomingPacketEvent) return false
        return fromEndpointId == other.fromEndpointId && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = fromEndpointId.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

interface MeshTransport {
    val connectedPeers: StateFlow<List<PeerConnection>>
    val incomingPackets: SharedFlow<IncomingPacketEvent>

    fun startAdvertising(
        networkId: String,
        localNodeId: String,
        displayName: String,
        role: NodeRole
    )

    fun startDiscovery(networkId: String)

    fun stopAll()

    fun sendBytes(endpointId: String, data: ByteArray): Boolean

    fun broadcastBytes(data: ByteArray, excludeEndpointId: String? = null): Int

    fun updateLocalRole(newRole: NodeRole)
}
