package com.example.mesh.protocol

import com.example.data.model.NodeRole
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object MeshProtocol {
    const val CURRENT_PROTOCOL_VERSION = 1

    // Routing Priorities (Section 8)
    const val PRIORITY_CONTROL = 0
    const val PRIORITY_HOST = 1
    const val PRIORITY_RELAY = 2
    const val PRIORITY_CLIENT = 3

    // Default Hop Limits (Section 7.3)
    const val HOP_LIMIT_HOST = 1024
    const val HOP_LIMIT_RELAY = 1024
    const val HOP_LIMIT_CLIENT = 3
    const val HOP_LIMIT_CONTROL = 16

    // Default Expiration (Section 7.3: ~30 minutes)
    const val DEFAULT_MESSAGE_TTL_MS = 30 * 60 * 1000L

    // Max plain text length (Section 9.5: ~1000 unicode characters)
    const val MAX_TEXT_LENGTH = 1000

    // Message Types
    const val TYPE_HOST_ANNOUNCEMENT = "HOST_ANNOUNCEMENT"
    const val TYPE_RELAY_ANNOUNCEMENT = "RELAY_ANNOUNCEMENT"
    const val TYPE_CLIENT_POST = "CLIENT_POST"
    const val TYPE_ASSISTANCE_ALERT = "ASSISTANCE_ALERT"
    const val TYPE_CONTROL_NODE_APPROVAL = "CONTROL_NODE_APPROVAL"
    const val TYPE_CONTROL_NETWORK_SHUTDOWN = "CONTROL_NETWORK_SHUTDOWN"
    const val TYPE_CONTROL_HANDSHAKE = "CONTROL_HANDSHAKE"
    const val TYPE_CONTROL_HANDSHAKE_ACK = "CONTROL_HANDSHAKE_ACK"
    const val TYPE_CATCHUP_REQUEST = "CATCHUP_REQUEST"
    const val TYPE_CATCHUP_RESPONSE = "CATCHUP_RESPONSE"

    // Channels (Section 1: #announcements, #general, #assistance)
    const val CHANNEL_ANNOUNCEMENTS = 0
    const val CHANNEL_GENERAL = 1
    const val CHANNEL_ASSISTANCE = 2
}

/**
 * Immutable packet envelope for mesh propagation.
 * Gateway-ready with channel partitioning, alerts, and optional location telemetry.
 */
data class MeshPacket(
    val protocolVersion: Int = MeshProtocol.CURRENT_PROTOCOL_VERSION,
    val networkId: String,
    val messageId: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderDisplayName: String,
    val senderRole: NodeRole,
    val messageType: String,
    val priority: Int,
    val channelId: Int = MeshProtocol.CHANNEL_GENERAL,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = createdAt + MeshProtocol.DEFAULT_MESSAGE_TTL_MS,
    val hopLimit: Int,
    val currentHopCount: Int = 0,
    val text: String,
    val originGrant: String? = null,
    val isAlert: Boolean = false,
    val locationUri: String? = null,
    val gatewayId: String? = null,
    val transportOrigin: String = "MESH"
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = now >= expiresAt

    fun canBeForwarded(): Boolean {
        // Must not exceed hop limit when forwarded and must not be expired
        return (currentHopCount + 1) < hopLimit && !isExpired()
    }

    /**
     * Create forwarded copy: currentHopCount incremented by 1.
     * Original sender, messageId, createdAt, role, and text are strictly preserved.
     */
    fun forForwarding(): MeshPacket {
        return copy(
            currentHopCount = currentHopCount + 1
        )
    }

    fun toByteArray(): ByteArray {
        val json = JSONObject().apply {
            put("v", protocolVersion)
            put("nid", networkId)
            put("mid", messageId)
            put("sid", senderId)
            put("sname", senderDisplayName)
            put("srole", senderRole.name)
            put("type", messageType)
            put("prio", priority)
            put("ch", channelId)
            put("cat", createdAt)
            put("exp", expiresAt)
            put("hlim", hopLimit)
            put("hcur", currentHopCount)
            put("txt", text)
            if (originGrant != null) put("grant", originGrant)
            if (isAlert) put("alrt", true)
            if (locationUri != null) put("loc", locationUri)
            if (gatewayId != null) put("gw", gatewayId)
            if (transportOrigin != "MESH") put("tor", transportOrigin)
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun fromByteArray(bytes: ByteArray): MeshPacket? {
            return try {
                val str = String(bytes, Charsets.UTF_8)
                val json = JSONObject(str)
                val version = json.optInt("v", 1)
                if (version != MeshProtocol.CURRENT_PROTOCOL_VERSION) return null

                val nid = json.getString("nid")
                val mid = json.getString("mid")
                val sid = json.getString("sid")
                val sname = json.getString("sname")
                val srole = NodeRole.fromString(json.getString("srole"))
                val type = json.getString("type")
                val prio = json.getInt("prio")
                val ch = json.optInt("ch", MeshProtocol.CHANNEL_GENERAL)
                val cat = json.getLong("cat")
                val exp = json.getLong("exp")
                val hlim = json.getInt("hlim")
                val hcur = json.getInt("hcur")
                val txt = json.getString("txt")
                val grant = if (json.has("grant")) json.getString("grant") else null
                val alrt = json.optBoolean("alrt", false)
                val loc = if (json.has("loc")) json.getString("loc") else null
                val gw = if (json.has("gw")) json.getString("gw") else null
                val tor = json.optString("tor", "MESH")

                MeshPacket(
                    protocolVersion = version,
                    networkId = nid,
                    messageId = mid,
                    senderId = sid,
                    senderDisplayName = sname,
                    senderRole = srole,
                    messageType = type,
                    priority = prio,
                    channelId = ch,
                    createdAt = cat,
                    expiresAt = exp,
                    hopLimit = hlim,
                    currentHopCount = hcur,
                    text = txt,
                    originGrant = grant,
                    isAlert = alrt,
                    locationUri = loc,
                    gatewayId = gw,
                    transportOrigin = tor
                )
            } catch (_: Exception) {
                null
            }
        }

        fun createHostAnnouncement(
            networkId: String,
            hostId: String,
            hostName: String,
            text: String,
            channelId: Int = MeshProtocol.CHANNEL_ANNOUNCEMENTS,
            grant: String? = null,
            locationUri: String? = null
        ): MeshPacket {
            val truncated = if (text.length > MeshProtocol.MAX_TEXT_LENGTH) text.substring(0, MeshProtocol.MAX_TEXT_LENGTH) else text
            return MeshPacket(
                networkId = networkId,
                senderId = hostId,
                senderDisplayName = hostName,
                senderRole = NodeRole.HOST,
                messageType = MeshProtocol.TYPE_HOST_ANNOUNCEMENT,
                priority = MeshProtocol.PRIORITY_HOST,
                channelId = channelId,
                hopLimit = MeshProtocol.HOP_LIMIT_HOST,
                text = truncated,
                originGrant = grant,
                locationUri = locationUri
            )
        }

        fun createRelayAnnouncement(
            networkId: String,
            nodeId: String,
            nodeName: String,
            text: String,
            channelId: Int = MeshProtocol.CHANNEL_ANNOUNCEMENTS,
            grant: String? = null,
            locationUri: String? = null
        ): MeshPacket {
            val truncated = if (text.length > MeshProtocol.MAX_TEXT_LENGTH) text.substring(0, MeshProtocol.MAX_TEXT_LENGTH) else text
            return MeshPacket(
                networkId = networkId,
                senderId = nodeId,
                senderDisplayName = nodeName,
                senderRole = NodeRole.RELAY_NODE,
                messageType = MeshProtocol.TYPE_RELAY_ANNOUNCEMENT,
                priority = MeshProtocol.PRIORITY_RELAY,
                channelId = channelId,
                hopLimit = MeshProtocol.HOP_LIMIT_RELAY,
                text = truncated,
                originGrant = grant,
                locationUri = locationUri
            )
        }

        fun createClientPost(
            networkId: String,
            clientId: String,
            clientName: String,
            text: String,
            channelId: Int = MeshProtocol.CHANNEL_GENERAL,
            locationUri: String? = null
        ): MeshPacket {
            val truncated = if (text.length > MeshProtocol.MAX_TEXT_LENGTH) text.substring(0, MeshProtocol.MAX_TEXT_LENGTH) else text
            return MeshPacket(
                networkId = networkId,
                senderId = clientId,
                senderDisplayName = clientName,
                senderRole = NodeRole.CLIENT,
                messageType = MeshProtocol.TYPE_CLIENT_POST,
                priority = MeshProtocol.PRIORITY_CLIENT,
                channelId = channelId,
                hopLimit = MeshProtocol.HOP_LIMIT_CLIENT, // max 3 hops
                text = truncated,
                locationUri = locationUri
            )
        }

        fun createAssistanceAlert(
            networkId: String,
            senderId: String,
            senderName: String,
            senderRole: NodeRole,
            text: String,
            locationUri: String? = null
        ): MeshPacket {
            val truncated = if (text.length > MeshProtocol.MAX_TEXT_LENGTH) text.substring(0, MeshProtocol.MAX_TEXT_LENGTH) else text
            return MeshPacket(
                networkId = networkId,
                senderId = senderId,
                senderDisplayName = senderName,
                senderRole = senderRole,
                messageType = MeshProtocol.TYPE_ASSISTANCE_ALERT,
                priority = MeshProtocol.PRIORITY_HOST, // Priority 1 for immediate propagation
                channelId = MeshProtocol.CHANNEL_ASSISTANCE,
                hopLimit = MeshProtocol.HOP_LIMIT_HOST,
                text = truncated,
                isAlert = true,
                locationUri = locationUri
            )
        }

        fun createShutdownNotice(
            networkId: String,
            hostId: String,
            hostName: String,
            secondsCountdown: Int = 60
        ): MeshPacket {
            return MeshPacket(
                networkId = networkId,
                senderId = hostId,
                senderDisplayName = hostName,
                senderRole = NodeRole.HOST,
                messageType = MeshProtocol.TYPE_CONTROL_NETWORK_SHUTDOWN,
                priority = MeshProtocol.PRIORITY_CONTROL,
                hopLimit = MeshProtocol.HOP_LIMIT_HOST,
                text = "Host has scheduled network closure in $secondsCountdown seconds."
            )
        }

        fun createNodeApprovalNotice(
            networkId: String,
            approverId: String,
            approverName: String,
            targetNodeId: String,
            targetNodeName: String,
            grant: String,
            canDelegate: Boolean
        ): MeshPacket {
            val payload = JSONObject().apply {
                put("targetId", targetNodeId)
                put("targetName", targetNodeName)
                put("grant", grant)
                put("canDelegate", canDelegate)
            }
            return MeshPacket(
                networkId = networkId,
                senderId = approverId,
                senderDisplayName = approverName,
                senderRole = NodeRole.HOST,
                messageType = MeshProtocol.TYPE_CONTROL_NODE_APPROVAL,
                priority = MeshProtocol.PRIORITY_CONTROL,
                hopLimit = MeshProtocol.HOP_LIMIT_CONTROL,
                text = payload.toString(),
                originGrant = grant
            )
        }
    }
}

/**
 * Handshake payload for peer connection.
 */
data class HandshakePacket(
    val protocolVersion: Int = MeshProtocol.CURRENT_PROTOCOL_VERSION,
    val networkId: String,
    val nodeId: String,
    val displayName: String,
    val role: NodeRole,
    val recentMessageIds: List<String> = emptyList()
) {
    fun toByteArray(): ByteArray {
        val json = JSONObject().apply {
            put("v", protocolVersion)
            put("nid", networkId)
            put("nodeId", nodeId)
            put("name", displayName)
            put("role", role.name)
            val arr = JSONArray()
            recentMessageIds.forEach { arr.put(it) }
            put("recentIds", arr)
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun fromByteArray(bytes: ByteArray): HandshakePacket? {
            return try {
                val json = JSONObject(String(bytes, Charsets.UTF_8))
                val ids = mutableListOf<String>()
                val arr = json.optJSONArray("recentIds")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        ids.add(arr.getString(i))
                    }
                }
                HandshakePacket(
                    protocolVersion = json.optInt("v", 1),
                    networkId = json.getString("nid"),
                    nodeId = json.getString("nodeId"),
                    displayName = json.getString("name"),
                    role = NodeRole.fromString(json.getString("role")),
                    recentMessageIds = ids
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
