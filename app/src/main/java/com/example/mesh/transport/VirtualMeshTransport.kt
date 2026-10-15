package com.example.mesh.transport

import com.example.data.model.NodeRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory virtual transport enabling multi-hop network graphs
 * for automated unit and routing verification (Tests A - K).
 */
class VirtualMeshTransport(
    val nodeId: String,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : MeshTransport {

    companion object {
        // Global registry of virtual transports indexed by nodeId
        private val networkNodes = ConcurrentHashMap<String, VirtualMeshTransport>()

        fun resetAll() {
            networkNodes.clear()
        }

        fun linkNodes(nodeA: VirtualMeshTransport, nodeB: VirtualMeshTransport) {
            nodeA.connectToPeer(nodeB.nodeId, nodeB.displayName, nodeB.localRole)
            nodeB.connectToPeer(nodeA.nodeId, nodeA.displayName, nodeA.localRole)
        }

        fun unlinkNodes(nodeA: VirtualMeshTransport, nodeB: VirtualMeshTransport) {
            nodeA.disconnectPeer(nodeB.nodeId)
            nodeB.disconnectPeer(nodeA.nodeId)
        }
    }

    init {
        networkNodes[nodeId] = this
    }

    private val _connectedPeers = MutableStateFlow<List<PeerConnection>>(emptyList())
    override val connectedPeers: StateFlow<List<PeerConnection>> = _connectedPeers.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<IncomingPacketEvent>(extraBufferCapacity = 64)
    override val incomingPackets: SharedFlow<IncomingPacketEvent> = _incomingPackets.asSharedFlow()

    var displayName: String = "Virtual-$nodeId"
    var localRole: NodeRole = NodeRole.CLIENT
    var activeNetworkId: String? = null

    override fun updateLocalRole(newRole: NodeRole) {
        localRole = newRole
    }

    override fun startAdvertising(
        networkId: String,
        localNodeId: String,
        displayName: String,
        role: NodeRole
    ) {
        this.activeNetworkId = networkId
        this.displayName = displayName
        this.localRole = role
    }

    override fun startDiscovery(networkId: String) {
        this.activeNetworkId = networkId
    }

    override fun stopAll() {
        val current = _connectedPeers.value
        _connectedPeers.value = emptyList()
        current.forEach { peer ->
            networkNodes[peer.endpointId]?.disconnectPeer(nodeId)
        }
    }

    fun connectToPeer(peerId: String, peerName: String, peerRole: NodeRole) {
        val existing = _connectedPeers.value.any { it.endpointId == peerId }
        if (!existing) {
            val peer = PeerConnection(
                endpointId = peerId,
                nodeId = peerId,
                displayName = peerName,
                role = peerRole
            )
            _connectedPeers.value = _connectedPeers.value + peer
        }
    }

    fun disconnectPeer(peerId: String) {
        _connectedPeers.value = _connectedPeers.value.filter { it.endpointId != peerId }
    }

    override fun sendBytes(endpointId: String, data: ByteArray): Boolean {
        val target = networkNodes[endpointId] ?: return false
        val copy = data.clone()
        target.receiveBytesFrom(nodeId, copy)
        return true
    }

    override fun broadcastBytes(data: ByteArray, excludeEndpointId: String?): Int {
        val peers = _connectedPeers.value.filter { it.endpointId != excludeEndpointId }
        var sent = 0
        peers.forEach { peer ->
            val target = networkNodes[peer.endpointId]
            if (target != null) {
                target.receiveBytesFrom(nodeId, data.clone())
                sent++
            }
        }
        return sent
    }

    fun receiveBytesFrom(fromNodeId: String, data: ByteArray) {
        val event = IncomingPacketEvent(fromNodeId, data)
        if (!_incomingPackets.tryEmit(event)) {
            scope.launch {
                _incomingPackets.emit(event)
            }
        }
    }
}
