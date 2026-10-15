package com.example.mesh.invitation

import android.net.Uri
import com.example.data.model.NodeRole
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Invitation capabilities (Section 4.5)
 */
enum class InvitationCapability {
    JOIN_CLIENT,
    JOIN_RELAY_NODE,
    JOIN_RELAY_NODE_AND_DELEGATE;

    fun toRole(): NodeRole {
        return when (this) {
            JOIN_CLIENT -> NodeRole.CLIENT
            JOIN_RELAY_NODE, JOIN_RELAY_NODE_AND_DELEGATE -> NodeRole.RELAY_NODE
        }
    }

    val canDelegate: Boolean
        get() = this == JOIN_RELAY_NODE_AND_DELEGATE
}

/**
 * QR Invitation structure (Section 5)
 */
data class MeshInvitation(
    val version: Int = 1,
    val networkId: String,
    val networkName: String,
    val inviterNodeId: String,
    val inviterDisplayName: String,
    val capability: InvitationCapability,
    val authGrant: String,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = createdAt + (24 * 60 * 60 * 1000L), // 24 hours
    val networkFingerprint: String
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = now >= expiresAt

    /**
     * Compact URI encoding for QR code or manual sharing
     */
    fun toInvitationUriString(): String {
        return Uri.Builder()
            .scheme("meshboard")
            .authority("invite")
            .appendQueryParameter("v", version.toString())
            .appendQueryParameter("nid", networkId)
            .appendQueryParameter("name", networkName)
            .appendQueryParameter("iid", inviterNodeId)
            .appendQueryParameter("iname", inviterDisplayName)
            .appendQueryParameter("cap", capability.name)
            .appendQueryParameter("grant", authGrant)
            .appendQueryParameter("cat", createdAt.toString())
            .appendQueryParameter("exp", expiresAt.toString())
            .appendQueryParameter("fp", networkFingerprint)
            .build()
            .toString()
    }

    companion object {
        /**
         * Generate a deterministic HMAC-like auth grant for offline verifiable delegation
         */
        fun generateAuthGrant(
            networkId: String,
            inviterNodeId: String,
            capability: InvitationCapability,
            createdAt: Long
        ): String {
            val raw = "$networkId:$inviterNodeId:${capability.name}:$createdAt:vivescript_mesh_salt"
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            return digest.take(12).joinToString("") { "%02x".format(it) }
        }

        fun generateFingerprint(networkId: String, networkName: String): String {
            val raw = "$networkId:$networkName:fingerprint"
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            return digest.take(6).joinToString("") { "%02x".format(it) }
        }

        fun create(
            networkId: String,
            networkName: String,
            inviterNodeId: String,
            inviterDisplayName: String,
            capability: InvitationCapability,
            ttlMs: Long = 24 * 60 * 60 * 1000L
        ): MeshInvitation {
            val now = System.currentTimeMillis()
            val grant = generateAuthGrant(networkId, inviterNodeId, capability, now)
            val fp = generateFingerprint(networkId, networkName)
            return MeshInvitation(
                networkId = networkId,
                networkName = networkName,
                inviterNodeId = inviterNodeId,
                inviterDisplayName = inviterDisplayName,
                capability = capability,
                authGrant = grant,
                createdAt = now,
                expiresAt = now + ttlMs,
                networkFingerprint = fp
            )
        }

        fun parse(input: String): MeshInvitation? {
            val trimmed = input.trim()
            if (trimmed.startsWith("meshboard://invite")) {
                return try {
                    val uri = Uri.parse(trimmed)
                    val v = uri.getQueryParameter("v")?.toIntOrNull() ?: 1
                    val nid = uri.getQueryParameter("nid") ?: return null
                    val name = uri.getQueryParameter("name") ?: return null
                    val iid = uri.getQueryParameter("iid") ?: ""
                    val iname = uri.getQueryParameter("iname") ?: ""
                    val capStr = uri.getQueryParameter("cap") ?: InvitationCapability.JOIN_CLIENT.name
                    val cap = try {
                        InvitationCapability.valueOf(capStr)
                    } catch (_: Exception) {
                        InvitationCapability.JOIN_CLIENT
                    }
                    val grant = uri.getQueryParameter("grant") ?: ""
                    val cat = uri.getQueryParameter("cat")?.toLongOrNull() ?: System.currentTimeMillis()
                    val exp = uri.getQueryParameter("exp")?.toLongOrNull() ?: (cat + 86400000L)
                    val fp = uri.getQueryParameter("fp") ?: ""

                    MeshInvitation(
                        version = v,
                        networkId = nid,
                        networkName = name,
                        inviterNodeId = iid,
                        inviterDisplayName = iname,
                        capability = cap,
                        authGrant = grant,
                        createdAt = cat,
                        expiresAt = exp,
                        networkFingerprint = fp
                    )
                } catch (_: Exception) {
                    null
                }
            }

            // Alternatively try JSON parsing
            return try {
                val json = JSONObject(trimmed)
                val nid = json.getString("nid")
                val name = json.getString("name")
                val capStr = json.optString("cap", InvitationCapability.JOIN_CLIENT.name)
                val cap = try {
                    InvitationCapability.valueOf(capStr)
                } catch (_: Exception) {
                    InvitationCapability.JOIN_CLIENT
                }
                MeshInvitation(
                    version = json.optInt("v", 1),
                    networkId = nid,
                    networkName = name,
                    inviterNodeId = json.optString("iid", ""),
                    inviterDisplayName = json.optString("iname", ""),
                    capability = cap,
                    authGrant = json.optString("grant", ""),
                    createdAt = json.optLong("cat", System.currentTimeMillis()),
                    expiresAt = json.optLong("exp", System.currentTimeMillis() + 86400000L),
                    networkFingerprint = json.optString("fp", "")
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
