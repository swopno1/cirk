package com.example.mesh.transport

import android.content.Context
import android.util.Log
import com.example.data.model.NodeRole
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.MessageDigest

class NearbyConnectionsTransport(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : MeshTransport {

    companion object {
        private const val TAG = "NearbyMeshTransport"
        const val MAX_PEER_CONNECTIONS = 8
    }

    private val connectionsClient = Nearby.getConnectionsClient(context.applicationContext)

    private val _connectedPeers = MutableStateFlow<List<PeerConnection>>(emptyList())
    override val connectedPeers: StateFlow<List<PeerConnection>> = _connectedPeers.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<IncomingPacketEvent>(extraBufferCapacity = 64)
    override val incomingPackets: SharedFlow<IncomingPacketEvent> = _incomingPackets.asSharedFlow()

    private var currentNetworkId: String? = null
    private var localNodeId: String = ""
    private var localDisplayName: String = ""
    private var localRole: NodeRole = NodeRole.CLIENT
    private var isAdvertising = false
    private var isDiscovering = false

    private val pendingEndpoints = mutableSetOf<String>()

    private fun getServiceId(networkId: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(networkId.toByteArray(Charsets.UTF_8))
        val hex = digest.take(6).joinToString("") { "%02x".format(it) }
        return "com.meshboard.net.$hex"
    }

    private fun buildEndpointName(): String {
        val json = JSONObject().apply {
            put("id", localNodeId)
            put("name", localDisplayName)
            put("role", localRole.name)
        }
        return json.toString()
    }

    private fun parseEndpointName(raw: String): Triple<String, String, NodeRole> {
        return try {
            val json = JSONObject(raw)
            Triple(
                json.optString("id", "unknown"),
                json.optString("name", "Peer"),
                NodeRole.fromString(json.optString("role", "CLIENT"))
            )
        } catch (_: Exception) {
            Triple("peer-$raw", raw, NodeRole.CLIENT)
        }
    }

    override fun updateLocalRole(newRole: NodeRole) {
        localRole = newRole
    }

    override fun startAdvertising(
        networkId: String,
        localNodeId: String,
        displayName: String,
        role: NodeRole
    ) {
        this.currentNetworkId = networkId
        this.localNodeId = localNodeId
        this.localDisplayName = displayName
        this.localRole = role

        if (isAdvertising) {
            connectionsClient.stopAdvertising()
            isAdvertising = false
        }

        val serviceId = getServiceId(networkId)
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startAdvertising(
            buildEndpointName(),
            serviceId,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            isAdvertising = true
            Log.d(TAG, "Nearby advertising started for service: $serviceId")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Nearby advertising failed: ${e.message}")
        }
    }

    override fun startDiscovery(networkId: String) {
        this.currentNetworkId = networkId
        if (isDiscovering) {
            connectionsClient.stopDiscovery()
            isDiscovering = false
        }

        val serviceId = getServiceId(networkId)
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(
            serviceId,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            isDiscovering = true
            Log.d(TAG, "Nearby discovery started for service: $serviceId")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Nearby discovery failed: ${e.message}")
        }
    }

    override fun stopAll() {
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping nearby connections: ${e.message}")
        }
        isAdvertising = false
        isDiscovering = false
        pendingEndpoints.clear()
        _connectedPeers.value = emptyList()
    }

    override fun sendBytes(endpointId: String, data: ByteArray): Boolean {
        return try {
            val payload = Payload.fromBytes(data)
            connectionsClient.sendPayload(endpointId, payload)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send bytes to $endpointId: ${e.message}")
            false
        }
    }

    override fun broadcastBytes(data: ByteArray, excludeEndpointId: String?): Int {
        val targets = _connectedPeers.value
            .map { it.endpointId }
            .filter { it != excludeEndpointId }

        if (targets.isEmpty()) return 0

        var sentCount = 0
        try {
            val payload = Payload.fromBytes(data)
            connectionsClient.sendPayload(targets, payload)
            sentCount = targets.size
        } catch (e: Exception) {
            Log.w(TAG, "Broadcast failed: ${e.message}")
        }
        return sentCount
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: $endpointId (${info.endpointName})")
            if (_connectedPeers.value.size >= MAX_PEER_CONNECTIONS) {
                Log.d(TAG, "Max peer limit reached, ignoring endpoint $endpointId")
                return
            }
            if (pendingEndpoints.contains(endpointId)) return

            pendingEndpoints.add(endpointId)
            connectionsClient.requestConnection(
                buildEndpointName(),
                endpointId,
                connectionLifecycleCallback
            ).addOnFailureListener { e ->
                Log.w(TAG, "Request connection failed to $endpointId: ${e.message}")
                pendingEndpoints.remove(endpointId)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
            pendingEndpoints.remove(endpointId)
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with $endpointId (${connectionInfo.endpointName})")
            // Auto accept connection in cluster
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to accept connection with $endpointId: ${e.message}")
                    pendingEndpoints.remove(endpointId)
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            pendingEndpoints.remove(endpointId)
            if (result.status.isSuccess) {
                Log.d(TAG, "Connected successfully to $endpointId")
                val parsed = parseEndpointName(endpointId)
                val peer = PeerConnection(
                    endpointId = endpointId,
                    nodeId = parsed.first,
                    displayName = parsed.second,
                    role = parsed.third
                )
                val updated = _connectedPeers.value.filter { it.endpointId != endpointId } + peer
                _connectedPeers.value = updated
            } else {
                Log.w(TAG, "Connection to $endpointId rejected or failed: ${result.status.statusCode}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            val updated = _connectedPeers.value.filter { it.endpointId != endpointId }
            _connectedPeers.value = updated
            pendingEndpoints.remove(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    scope.launch {
                        _incomingPackets.emit(IncomingPacketEvent(endpointId, bytes))
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Transfer status update
        }
    }
}
