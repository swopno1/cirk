package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MeshBoardApp
import com.example.data.model.*
import com.example.mesh.invitation.InvitationCapability
import com.example.mesh.invitation.MeshInvitation
import com.example.mesh.routing.NetworkMeshStatus
import com.example.service.MeshForegroundService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class MeshUiState(
    val userProfile: UserProfile? = null,
    val activeNetwork: NetworkInfo? = null,
    val meshStatus: NetworkMeshStatus = NetworkMeshStatus.DISCONNECTED,
    val connectedPeerCount: Int = 0,
    val activeRelayCount: Int = 0,
    val shutdownCountdown: Int? = null,
    val messages: List<MeshMessageEntity> = emptyList(),
    val approvedNodes: List<NodeAuthorizationEntity> = emptyList(),
    val isBatterySaverActive: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class MeshViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MeshBoardApp
    private val database = app.database
    val router = app.meshRouter!!

    private val _isBatterySaver = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _infoMessage = MutableStateFlow<String?>(null)

    val profileFlow = database.userProfileDao().observeProfile()
    val activeNetworkFlow = database.networkInfoDao().observeActiveNetwork()

    val messagesFlow: Flow<List<MeshMessageEntity>> = activeNetworkFlow.flatMapLatest { net ->
        if (net != null) {
            database.messageDao().getMessagesForNetwork(net.networkId)
        } else {
            flowOf(emptyList())
        }
    }

    val approvedNodesFlow: Flow<List<NodeAuthorizationEntity>> = activeNetworkFlow.flatMapLatest { net ->
        if (net != null) {
            database.nodeAuthDao().observeApprovedNodes(net.networkId)
        } else {
            flowOf(emptyList())
        }
    }

    private data class NetState(
        val profile: UserProfile?,
        val network: NetworkInfo?,
        val status: NetworkMeshStatus,
        val peers: Int,
        val relays: Int
    )

    private data class ContentState(
        val countdown: Int?,
        val messages: List<MeshMessageEntity>,
        val approvedNodes: List<NodeAuthorizationEntity>,
        val batterySaver: Boolean
    )

    private val netStateFlow = combine(
        profileFlow,
        activeNetworkFlow,
        router.meshStatus,
        router.connectedPeerCount,
        router.activeRelayCount
    ) { profile, network, status, peers, relays ->
        NetState(profile, network, status, peers, relays)
    }

    private val contentStateFlow = combine(
        router.shutdownCountdown,
        messagesFlow,
        approvedNodesFlow,
        _isBatterySaver
    ) { countdown, msgs, nodes, battery ->
        ContentState(countdown, msgs, nodes, battery)
    }

    private val messageStateFlow = combine(
        _errorMessage,
        _infoMessage
    ) { err, info ->
        Pair(err, info)
    }

    val uiState: StateFlow<MeshUiState> = combine(
        netStateFlow,
        contentStateFlow,
        messageStateFlow
    ) { net, content, (err, info) ->
        MeshUiState(
            userProfile = net.profile,
            activeNetwork = net.network,
            meshStatus = net.status,
            connectedPeerCount = net.peers,
            activeRelayCount = net.relays,
            shutdownCountdown = content.countdown,
            messages = content.messages,
            approvedNodes = content.approvedNodes,
            isBatterySaverActive = content.batterySaver,
            errorMessage = err,
            infoMessage = info
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MeshUiState()
    )

    init {
        // Auto-resume router if active network exists in DB
        viewModelScope.launch {
            val net = database.networkInfoDao().getActiveNetwork()
            val profile = database.userProfileDao().getProfile()
            if (net != null && profile != null) {
                router.start(
                    network = net,
                    nodeId = profile.profileId,
                    displayName = if (net.isHost) net.hostDisplayName else profile.displayName,
                    role = net.userRole
                )
                MeshForegroundService.startService(getApplication())
            }
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun dismissInfo() {
        _infoMessage.value = null
    }

    fun updateDisplayName(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val current = database.userProfileDao().getProfile()
            val updated = current?.copy(displayName = trimmed) ?: UserProfile(
                profileId = UUID.randomUUID().toString(),
                displayName = trimmed
            )
            database.userProfileDao().saveProfile(updated)
            _infoMessage.value = "Display name updated"
        }
    }

    fun createNetwork(
        networkName: String,
        hostAlias: String,
        description: String = "",
        canDelegate: Boolean = true
    ) {
        val name = networkName.trim()
        val alias = hostAlias.trim()
        if (name.isBlank() || alias.isBlank()) {
            _errorMessage.value = "Network name and Host alias are required"
            return
        }

        viewModelScope.launch {
            val profile = database.userProfileDao().getProfile() ?: run {
                val p = UserProfile(UUID.randomUUID().toString(), alias)
                database.userProfileDao().saveProfile(p)
                p
            }

            val networkId = UUID.randomUUID().toString()
            val network = NetworkInfo(
                networkId = networkId,
                networkName = name,
                hostDisplayName = alias,
                hostNodeId = profile.profileId,
                description = description.trim(),
                isHost = true,
                userRole = NodeRole.HOST,
                canDelegate = canDelegate,
                isActive = true
            )

            database.networkInfoDao().saveNetwork(network)

            // Start router
            router.start(
                network = network,
                nodeId = profile.profileId,
                displayName = alias,
                role = NodeRole.HOST
            )

            // Broadcast initial welcome announcement from Host
            router.publishMessage("Network \"$name\" created. Mesh is online.")

            MeshForegroundService.startService(getApplication())
            _infoMessage.value = "Network \"$name\" created as Host"
        }
    }

    fun joinNetwork(invitation: MeshInvitation) {
        if (invitation.isExpired()) {
            _errorMessage.value = "Invitation is expired"
            return
        }

        viewModelScope.launch {
            val profile = database.userProfileDao().getProfile() ?: run {
                val p = UserProfile(UUID.randomUUID().toString(), "Node-" + UUID.randomUUID().toString().take(4).uppercase())
                database.userProfileDao().saveProfile(p)
                p
            }

            val role = invitation.capability.toRole()
            val network = NetworkInfo(
                networkId = invitation.networkId,
                networkName = invitation.networkName,
                hostDisplayName = invitation.inviterDisplayName,
                hostNodeId = invitation.inviterNodeId,
                description = "Joined via QR Invitation",
                isHost = false,
                userRole = role,
                canDelegate = invitation.capability.canDelegate,
                isActive = true
            )

            database.networkInfoDao().saveNetwork(network)

            // If joined as Relay Node, record authorization grant
            if (role == NodeRole.RELAY_NODE) {
                val auth = NodeAuthorizationEntity(
                    nodeId = profile.profileId,
                    networkId = invitation.networkId,
                    displayName = profile.displayName,
                    role = NodeRole.RELAY_NODE,
                    approvedByNodeId = invitation.inviterNodeId,
                    canDelegate = invitation.capability.canDelegate,
                    authGrant = invitation.authGrant
                )
                database.nodeAuthDao().insertNodeAuth(auth)
            }

            router.start(
                network = network,
                nodeId = profile.profileId,
                displayName = profile.displayName,
                role = role
            )

            MeshForegroundService.startService(getApplication())
            _infoMessage.value = "Joined \"${invitation.networkName}\" as ${role.name}"
        }
    }

    fun publishMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        if (trimmed.length > 1000) {
            _errorMessage.value = "Message exceeds 1,000 character limit"
            return
        }

        viewModelScope.launch {
            val sent = router.publishMessage(trimmed)
            if (sent == null) {
                _errorMessage.value = "Failed to publish message"
            }
        }
    }

    fun leaveNetwork() {
        val net = uiState.value.activeNetwork ?: return
        viewModelScope.launch {
            router.stop()
            MeshForegroundService.stopService(getApplication())
            database.networkInfoDao().deactivateNetwork(net.networkId)
            _infoMessage.value = "Disconnected from network"
        }
    }

    fun shutdownNetwork(countdownSeconds: Int = 60) {
        viewModelScope.launch {
            router.broadcastShutdown(countdownSeconds)
            _infoMessage.value = "Network shutdown scheduled ($countdownSeconds s)"
        }
    }

    fun approvePeerAsRelay(peerNodeId: String, peerDisplayName: String, canDelegate: Boolean = true) {
        val net = uiState.value.activeNetwork ?: return
        val profile = uiState.value.userProfile ?: return
        if (net.userRole != NodeRole.HOST && !net.canDelegate) {
            _errorMessage.value = "Only Host or authorized delegators can approve Relay Nodes"
            return
        }

        viewModelScope.launch {
            val grant = MeshInvitation.generateAuthGrant(
                networkId = net.networkId,
                inviterNodeId = profile.profileId,
                capability = if (canDelegate) InvitationCapability.JOIN_RELAY_NODE_AND_DELEGATE else InvitationCapability.JOIN_RELAY_NODE,
                createdAt = System.currentTimeMillis()
            )

            val auth = NodeAuthorizationEntity(
                nodeId = peerNodeId,
                networkId = net.networkId,
                displayName = peerDisplayName,
                role = NodeRole.RELAY_NODE,
                approvedByNodeId = profile.profileId,
                canDelegate = canDelegate,
                authGrant = grant
            )
            database.nodeAuthDao().insertNodeAuth(auth)

            // Broadcast approval notice
            val notice = com.example.mesh.protocol.MeshPacket.createNodeApprovalNotice(
                networkId = net.networkId,
                approverId = profile.profileId,
                approverName = profile.displayName,
                targetNodeId = peerNodeId,
                targetNodeName = peerDisplayName,
                grant = grant,
                canDelegate = canDelegate
            )
            router.transport.broadcastBytes(notice.toByteArray())
            _infoMessage.value = "Approved $peerDisplayName as Relay Node"
        }
    }

    fun revokePeerRelay(nodeId: String) {
        val net = uiState.value.activeNetwork ?: return
        viewModelScope.launch {
            database.nodeAuthDao().revokeNode(nodeId, net.networkId)
            _infoMessage.value = "Revoked node relay authorization"
        }
    }

    fun clearMessageHistory() {
        val net = uiState.value.activeNetwork ?: return
        viewModelScope.launch {
            database.messageDao().clearNetworkMessages(net.networkId)
            _infoMessage.value = "Local message history cleared"
        }
    }

    fun toggleBatterySaver(enabled: Boolean) {
        _isBatterySaver.value = enabled
        _infoMessage.value = if (enabled) "Battery-conscious mode enabled" else "Standard mode enabled"
    }
}
