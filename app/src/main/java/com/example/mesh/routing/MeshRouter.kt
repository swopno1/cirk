package com.example.mesh.routing

import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.mesh.protocol.HandshakePacket
import com.example.mesh.protocol.MeshPacket
import com.example.mesh.protocol.MeshProtocol
import com.example.mesh.transport.MeshTransport
import com.example.mesh.transport.PeerConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

enum class NetworkMeshStatus {
    DISCONNECTED,
    HOST_ACTIVE,
    RELAY_CONNECTED,
    HOST_OFFLINE_MESH_CONTINUING,
    CLIENT_CONNECTED,
    SHUTTING_DOWN
}

class MeshRouter(
    val transport: MeshTransport,
    private val database: AppDatabase,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val onNewMessageDelivered: ((MeshMessageEntity) -> Unit)? = null
) {
    companion object {
        private const val TAG = "MeshRouter"
        const val SEEN_CACHE_MAX_SIZE = 5000
    }

    private val seenMessageCache = ConcurrentHashMap<String, Long>()
    private val knownPeerNodes = ConcurrentHashMap<String, PeerConnection>()

    private val _meshStatus = MutableStateFlow(NetworkMeshStatus.DISCONNECTED)
    val meshStatus: StateFlow<NetworkMeshStatus> = _meshStatus.asStateFlow()

    private val _connectedPeerCount = MutableStateFlow(0)
    val connectedPeerCount: StateFlow<Int> = _connectedPeerCount.asStateFlow()

    private val _activeRelayCount = MutableStateFlow(0)
    val activeRelayCount: StateFlow<Int> = _activeRelayCount.asStateFlow()

    private val _shutdownCountdown = MutableStateFlow<Int?>(null)
    val shutdownCountdown: StateFlow<Int?> = _shutdownCountdown.asStateFlow()

    private val _lastPacketReceivedTime = MutableStateFlow(0L)
    val lastPacketReceivedTime: StateFlow<Long> = _lastPacketReceivedTime.asStateFlow()

    private val _pendingQueueCount = MutableStateFlow(0)
    val pendingQueueCount: StateFlow<Int> = _pendingQueueCount.asStateFlow()

    private val _isBatterySaverActive = MutableStateFlow(false)
    val isBatterySaverActive: StateFlow<Boolean> = _isBatterySaverActive.asStateFlow()

    private val _activeTransportType = MutableStateFlow("Bluetooth LE / Wi-Fi Direct")
    val activeTransportType: StateFlow<String> = _activeTransportType.asStateFlow()

    private var lastAlertTimestamp: Long = 0L

    var currentNetwork: NetworkInfo? = null
        private set
    var localNodeId: String = ""
        private set
    var localDisplayName: String = ""
        private set
    var localRole: NodeRole = NodeRole.CLIENT
        private set

    private var packetListenerJob: Job? = null
    private var peerListenerJob: Job? = null
    private var queueProcessorJob: Job? = null

    fun start(
        network: NetworkInfo,
        nodeId: String,
        displayName: String,
        role: NodeRole,
        enablePeriodicQueueProcessor: Boolean = true
    ) {
        this.currentNetwork = network
        this.localNodeId = nodeId
        this.localDisplayName = displayName
        this.localRole = role

        transport.updateLocalRole(role)
        transport.startAdvertising(network.networkId, nodeId, displayName, role)
        transport.startDiscovery(network.networkId)

        updateStatusBasedOnRoleAndPeers(role, transport.connectedPeers.value)

        // Observe incoming packets
        packetListenerJob?.cancel()
        packetListenerJob = scope.launch {
            transport.incomingPackets.collect { event ->
                handleIncomingPacket(event.fromEndpointId, event.data)
            }
        }

        // Observe connected peers
        peerListenerJob?.cancel()
        peerListenerJob = scope.launch {
            transport.connectedPeers.collect { peers ->
                _connectedPeerCount.value = peers.size
                _activeRelayCount.value = peers.count { it.role == NodeRole.RELAY_NODE || it.role == NodeRole.HOST }
                updateStatusBasedOnRoleAndPeers(localRole, peers)

                // Handshake with newly connected peers for catch-up
                peers.forEach { peer ->
                    if (!knownPeerNodes.containsKey(peer.endpointId)) {
                        knownPeerNodes[peer.endpointId] = peer
                        sendHandshake(peer.endpointId)
                    }
                }
                knownPeerNodes.keys.retainAll(peers.map { it.endpointId }.toSet())
            }
        }

        // Start bounded store-and-forward queue processor if enabled
        queueProcessorJob?.cancel()
        if (enablePeriodicQueueProcessor) {
            queueProcessorJob = scope.launch {
                while (isActive) {
                    delay(3000)
                    processPendingQueue()
                }
            }
        }
    }

    fun stop() {
        packetListenerJob?.cancel()
        peerListenerJob?.cancel()
        queueProcessorJob?.cancel()
        transport.stopAll()
        knownPeerNodes.clear()
        _meshStatus.value = NetworkMeshStatus.DISCONNECTED
        _connectedPeerCount.value = 0
        _activeRelayCount.value = 0
    }

    fun updateRole(newRole: NodeRole) {
        localRole = newRole
        transport.updateLocalRole(newRole)
        currentNetwork?.let { net ->
            currentNetwork = net.copy(userRole = newRole)
            scope.launch {
                database.networkInfoDao().updateRole(net.networkId, newRole)
            }
        }
        updateStatusBasedOnRoleAndPeers(newRole, transport.connectedPeers.value)
    }

    private fun updateStatusBasedOnRoleAndPeers(role: NodeRole, peers: List<PeerConnection>) {
        if (_shutdownCountdown.value != null) {
            _meshStatus.value = NetworkMeshStatus.SHUTTING_DOWN
            return
        }

        if (role == NodeRole.HOST) {
            _meshStatus.value = NetworkMeshStatus.HOST_ACTIVE
            return
        }

        if (peers.isEmpty()) {
            _meshStatus.value = NetworkMeshStatus.DISCONNECTED
            return
        }

        val hasHostConnected = peers.any { it.role == NodeRole.HOST }
        if (hasHostConnected) {
            _meshStatus.value = if (role == NodeRole.RELAY_NODE) {
                NetworkMeshStatus.RELAY_CONNECTED
            } else {
                NetworkMeshStatus.CLIENT_CONNECTED
            }
        } else {
            // Section 12.1: Unexpected Host disconnection:
            // "Host offline — Mesh continuing"
            _meshStatus.value = NetworkMeshStatus.HOST_OFFLINE_MESH_CONTINUING
        }
    }

    /**
     * Send a handshake packet with recent message IDs for catch-up sync.
     */
    private suspend fun sendHandshake(targetEndpointId: String) {
        val net = currentNetwork ?: return
        val recentIds = database.messageDao().getRecentMessageIds(net.networkId)
        val handshake = HandshakePacket(
            networkId = net.networkId,
            nodeId = localNodeId,
            displayName = localDisplayName,
            role = localRole,
            recentMessageIds = recentIds
        )
        transport.sendBytes(targetEndpointId, handshake.toByteArray())
    }

    /**
     * Publish a new message from the local device to a specific channel.
     */
    suspend fun publishMessage(
        text: String,
        channelId: Int = MeshProtocol.CHANNEL_GENERAL,
        locationUri: String? = null
    ): MeshMessageEntity? {
        val net = currentNetwork ?: return null
        if (text.isBlank()) return null

        // Channel 0 (#announcements) is strictly reserved for Host and authorized Relay Nodes
        if (channelId == MeshProtocol.CHANNEL_ANNOUNCEMENTS && localRole == NodeRole.CLIENT) {
            throw IllegalStateException("Only Host and Relay nodes can publish to #announcements")
        }

        val packet = when (localRole) {
            NodeRole.HOST -> MeshPacket.createHostAnnouncement(net.networkId, localNodeId, localDisplayName, text, channelId = channelId, locationUri = locationUri)
            NodeRole.RELAY_NODE -> MeshPacket.createRelayAnnouncement(net.networkId, localNodeId, localDisplayName, text, channelId = channelId, locationUri = locationUri)
            NodeRole.CLIENT -> MeshPacket.createClientPost(net.networkId, localNodeId, localDisplayName, text, channelId = channelId, locationUri = locationUri)
        }

        // Mark seen locally
        markAsSeen(packet.messageId, net.networkId)

        val entity = MeshMessageEntity(
            messageId = packet.messageId,
            networkId = net.networkId,
            senderId = packet.senderId,
            senderDisplayName = packet.senderDisplayName,
            senderRole = packet.senderRole,
            messageType = packet.messageType,
            priority = packet.priority,
            channelId = packet.channelId,
            createdAt = packet.createdAt,
            receivedAt = System.currentTimeMillis(),
            expiresAt = packet.expiresAt,
            hopLimit = packet.hopLimit,
            currentHopCount = 0,
            text = packet.text,
            originGrant = packet.originGrant,
            isAlert = packet.isAlert,
            locationUri = packet.locationUri,
            deliveryStatus = MessageDeliveryStatus.CREATED_LOCALLY
        )

        database.messageDao().insertMessage(entity)

        // Broadcast to all connected peers
        val sent = transport.broadcastBytes(packet.toByteArray())
        if (sent > 0) {
            database.messageDao().updateDeliveryStatus(packet.messageId, MessageDeliveryStatus.FORWARDED_TO_PEERS)
        } else {
            // Enqueue in pending store-and-forward queue
            enqueuePendingPacket(packet)
        }

        return entity
    }

    /**
     * Broadcast an urgent Community Assistance Alert with 15-minute rate-limiting.
     */
    suspend fun publishAssistanceAlert(
        text: String,
        locationUri: String? = null
    ): MeshMessageEntity? {
        val net = currentNetwork ?: return null
        if (text.isBlank()) return null

        val now = System.currentTimeMillis()
        if (now - lastAlertTimestamp < 15 * 60 * 1000L && lastAlertTimestamp != 0L) {
            val waitMinutes = ((15 * 60 * 1000L - (now - lastAlertTimestamp)) / 60000) + 1
            throw IllegalStateException("Please wait $waitMinutes minute(s) before sending another assistance alert.")
        }
        lastAlertTimestamp = now

        val packet = MeshPacket.createAssistanceAlert(
            networkId = net.networkId,
            senderId = localNodeId,
            senderName = localDisplayName,
            senderRole = localRole,
            text = text,
            locationUri = locationUri
        )

        markAsSeen(packet.messageId, net.networkId)

        val entity = MeshMessageEntity(
            messageId = packet.messageId,
            networkId = net.networkId,
            senderId = packet.senderId,
            senderDisplayName = packet.senderDisplayName,
            senderRole = packet.senderRole,
            messageType = packet.messageType,
            priority = packet.priority,
            channelId = packet.channelId,
            createdAt = packet.createdAt,
            receivedAt = System.currentTimeMillis(),
            expiresAt = packet.expiresAt,
            hopLimit = packet.hopLimit,
            currentHopCount = 0,
            text = packet.text,
            originGrant = packet.originGrant,
            isAlert = true,
            locationUri = packet.locationUri,
            deliveryStatus = MessageDeliveryStatus.CREATED_LOCALLY
        )

        database.messageDao().insertMessage(entity)

        val sent = transport.broadcastBytes(packet.toByteArray())
        if (sent > 0) {
            database.messageDao().updateDeliveryStatus(packet.messageId, MessageDeliveryStatus.FORWARDED_TO_PEERS)
        } else {
            enqueuePendingPacket(packet)
        }

        return entity
    }

    suspend fun pinMessage(messageId: String, channelId: Int) {
        val net = currentNetwork ?: return
        if (localRole == NodeRole.CLIENT) return
        database.messageDao().clearPinnedInChannel(net.networkId, channelId)
        database.messageDao().setPinnedStatus(messageId, true)
    }

    suspend fun unpinMessage(messageId: String) {
        database.messageDao().setPinnedStatus(messageId, false)
    }

    suspend fun muteAuthor(authorId: String, authorName: String) {
        database.mutedAuthorDao().muteAuthor(
            com.example.data.model.MutedAuthorEntity(authorId = authorId, authorDisplayName = authorName)
        )
    }

    suspend fun unmuteAuthor(authorId: String) {
        database.mutedAuthorDao().unmuteAuthor(authorId)
    }

    fun setBatterySaverActive(active: Boolean) {
        _isBatterySaverActive.value = active
    }

    /**
     * Broadcast network shutdown notice (Section 12.3).
     */
    suspend fun broadcastShutdown(countdownSeconds: Int = 60) {
        val net = currentNetwork ?: return
        if (localRole != NodeRole.HOST) return

        val packet = MeshPacket.createShutdownNotice(net.networkId, localNodeId, localDisplayName, countdownSeconds)
        markAsSeen(packet.messageId, net.networkId)
        transport.broadcastBytes(packet.toByteArray())
        startLocalShutdownCountdown(countdownSeconds)
    }

    private fun startLocalShutdownCountdown(seconds: Int) {
        _shutdownCountdown.value = seconds
        _meshStatus.value = NetworkMeshStatus.SHUTTING_DOWN
        scope.launch {
            for (s in seconds downTo 1) {
                _shutdownCountdown.value = s
                delay(1000)
            }
            _shutdownCountdown.value = 0
            stop()
        }
    }

    /**
     * Process incoming packet from an ingress peer endpoint.
     */
    suspend fun handleIncomingPacket(fromEndpointId: String, data: ByteArray) {
        val net = currentNetwork ?: return

        // 1. Check for handshake packet
        val handshake = HandshakePacket.fromByteArray(data)
        if (handshake != null && handshake.networkId == net.networkId) {
            handleHandshake(fromEndpointId, handshake)
            return
        }

        // 2. Parse general MeshPacket
        val packet = MeshPacket.fromByteArray(data)
        if (packet == null) {
            Log.w(TAG, "Rejected malformed or unsupported packet")
            return
        }

        // 3. Confirm network match
        if (packet.networkId != net.networkId) {
            Log.w(TAG, "Discarded packet for different network: ${packet.networkId}")
            return
        }

        // 4. Discard expired packets
        if (packet.isExpired()) {
            Log.d(TAG, "Discarded expired packet: ${packet.messageId}")
            return
        }

        // 5. Section 9.1: Duplicate suppression
        if (isSeen(packet.messageId)) {
            Log.d(TAG, "Duplicate suppressed: ${packet.messageId}")
            return
        }

        // Mark as seen immediately
        markAsSeen(packet.messageId, net.networkId)

        // 6. Handle Control Packets
        if (packet.messageType == MeshProtocol.TYPE_CONTROL_NETWORK_SHUTDOWN) {
            handleShutdownNotice(packet)
            forwardIfEligible(packet, fromEndpointId)
            return
        }

        if (packet.messageType == MeshProtocol.TYPE_CONTROL_NODE_APPROVAL) {
            handleNodeApprovalNotice(packet)
            forwardIfEligible(packet, fromEndpointId)
            return
        }

        // Update health telemetry
        _lastPacketReceivedTime.value = System.currentTimeMillis()

        // 7. Insert message into local Room database
        val entity = MeshMessageEntity(
            messageId = packet.messageId,
            networkId = packet.networkId,
            senderId = packet.senderId,
            senderDisplayName = packet.senderDisplayName,
            senderRole = packet.senderRole,
            messageType = packet.messageType,
            priority = packet.priority,
            channelId = packet.channelId,
            createdAt = packet.createdAt,
            receivedAt = System.currentTimeMillis(),
            expiresAt = packet.expiresAt,
            hopLimit = packet.hopLimit,
            currentHopCount = packet.currentHopCount,
            text = packet.text,
            originGrant = packet.originGrant,
            isAlert = packet.isAlert,
            locationUri = packet.locationUri,
            deliveryStatus = MessageDeliveryStatus.RECEIVED_FROM_NETWORK
        )

        val inserted = database.messageDao().insertMessage(entity)
        val isMuted = database.mutedAuthorDao().isMuted(packet.senderId)
        if (inserted > 0 && packet.senderId != localNodeId && !isMuted) {
            // Trigger local notification
            onNewMessageDelivered?.invoke(entity)
        }

        // 8. Forward message if eligible
        forwardIfEligible(packet, fromEndpointId)
    }

    private suspend fun handleHandshake(fromEndpointId: String, handshake: HandshakePacket) {
        val net = currentNetwork ?: return
        // Catch-up synchronization (Section 11):
        // Send recent messages that the remote node does not have
        val localRecentIds = database.messageDao().getRecentMessageIds(net.networkId)
        val missingIds = localRecentIds.filter { !handshake.recentMessageIds.contains(it) }.take(50)

        if (missingIds.isNotEmpty()) {
            val messagesToSend = database.messageDao().getMessagesByIds(net.networkId, missingIds)
            messagesToSend.forEach { msg ->
                val pkt = MeshPacket(
                    protocolVersion = MeshProtocol.CURRENT_PROTOCOL_VERSION,
                    networkId = msg.networkId,
                    messageId = msg.messageId,
                    senderId = msg.senderId,
                    senderDisplayName = msg.senderDisplayName,
                    senderRole = msg.senderRole,
                    messageType = msg.messageType,
                    priority = msg.priority,
                    createdAt = msg.createdAt,
                    expiresAt = msg.expiresAt,
                    hopLimit = msg.hopLimit,
                    currentHopCount = msg.currentHopCount + 1,
                    text = msg.text,
                    originGrant = msg.originGrant
                )
                if (!pkt.isExpired()) {
                    transport.sendBytes(fromEndpointId, pkt.toByteArray())
                }
            }
        }
    }

    private fun handleShutdownNotice(packet: MeshPacket) {
        Log.i(TAG, "Network shutdown notice received from Host")
        startLocalShutdownCountdown(60)
    }

    private suspend fun handleNodeApprovalNotice(packet: MeshPacket) {
        val net = currentNetwork ?: return
        try {
            val json = org.json.JSONObject(packet.text)
            val targetId = json.getString("targetId")
            val targetName = json.getString("targetName")
            val grant = json.getString("grant")
            val canDelegate = json.getBoolean("canDelegate")

            val nodeAuth = NodeAuthorizationEntity(
                nodeId = targetId,
                networkId = net.networkId,
                displayName = targetName,
                role = NodeRole.RELAY_NODE,
                approvedByNodeId = packet.senderId,
                canDelegate = canDelegate,
                authGrant = grant
            )
            database.nodeAuthDao().insertNodeAuth(nodeAuth)

            if (targetId == localNodeId && localRole == NodeRole.CLIENT) {
                updateRole(NodeRole.RELAY_NODE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse node approval payload: ${e.message}")
        }
    }

    /**
     * Multi-Hop Forwarding Engine (Section 7.1)
     * Forwards eligible packets excluding ingress peer.
     */
    private suspend fun forwardIfEligible(packet: MeshPacket, ingressEndpointId: String) {
        // Section 7.1: Ordinary Client devices must not relay general traffic!
        if (localRole == NodeRole.CLIENT) {
            return
        }

        // Local role is HOST or RELAY_NODE: Check hopLimit & expiration
        if (!packet.canBeForwarded()) {
            Log.d(TAG, "Packet reached hop limit (${packet.currentHopCount}/${packet.hopLimit}) or expired")
            return
        }

        // Prepare forwarded copy with currentHopCount + 1
        val forwarded = packet.forForwarding()

        // Exclude ingress peer to prevent relay loops (Section 9.2)
        val sent = transport.broadcastBytes(forwarded.toByteArray(), excludeEndpointId = ingressEndpointId)
        if (sent > 0) {
            database.messageDao().updateDeliveryStatus(packet.messageId, MessageDeliveryStatus.FORWARDED_TO_PEERS)
        } else {
            // Enqueue for later when peers become reachable
            enqueuePendingPacket(forwarded)
        }
    }

    private suspend fun enqueuePendingPacket(packet: MeshPacket) {
        val net = currentNetwork ?: return
        val entry = PendingQueueEntity(
            messageId = packet.messageId,
            networkId = net.networkId,
            priority = packet.priority,
            payloadJson = String(packet.toByteArray(), Charsets.UTF_8),
            createdAt = packet.createdAt,
            expiresAt = packet.expiresAt
        )
        database.pendingQueueDao().enqueue(entry)
    }

    private suspend fun processPendingQueue() {
        val net = currentNetwork ?: return
        if (transport.connectedPeers.value.isEmpty()) return

        database.pendingQueueDao().pruneExpired()
        val pendingList = database.pendingQueueDao().getPendingPackets(net.networkId)
        for (item in pendingList) {
            val bytes = item.payloadJson.toByteArray(Charsets.UTF_8)
            val sent = transport.broadcastBytes(bytes)
            if (sent > 0) {
                database.pendingQueueDao().deleteById(item.id)
                database.messageDao().updateDeliveryStatus(item.messageId, MessageDeliveryStatus.FORWARDED_TO_PEERS)
            } else {
                database.pendingQueueDao().incrementAttempts(item.id)
            }
        }
    }

    private suspend fun isSeen(messageId: String): Boolean {
        if (seenMessageCache.containsKey(messageId)) return true
        val inDb = database.seenMessageDao().isSeen(messageId)
        if (inDb) seenMessageCache[messageId] = System.currentTimeMillis()
        return inDb
    }

    private suspend fun markAsSeen(messageId: String, networkId: String) {
        val now = System.currentTimeMillis()
        if (seenMessageCache.size > SEEN_CACHE_MAX_SIZE) {
            // Prune oldest 20%
            seenMessageCache.clear()
        }
        seenMessageCache[messageId] = now
        database.seenMessageDao().markSeen(SeenMessageEntity(messageId, networkId, now))
    }
}
