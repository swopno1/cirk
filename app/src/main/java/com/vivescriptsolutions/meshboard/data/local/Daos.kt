package com.vivescriptsolutions.meshboard.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles LIMIT 1")
    suspend fun getProfile(): Profile?

    @Query("SELECT * FROM profiles LIMIT 1")
    fun observeProfile(): Flow<Profile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: Profile)

    @Query("DELETE FROM profiles")
    suspend fun deleteProfile()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE networkId = :networkId ORDER BY timestamp DESC LIMIT 2000")
    fun getMessagesForNetwork(networkId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): Message?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: Message): Long

    @Query("UPDATE messages SET deliveryStatus = :status WHERE messageId = :messageId")
    suspend fun updateDeliveryStatus(messageId: String, status: String)

    @Query("SELECT messageId FROM messages WHERE networkId = :networkId ORDER BY timestamp DESC LIMIT 100")
    suspend fun getRecentMessageIds(networkId: String): List<String>

    @Query("SELECT * FROM messages WHERE networkId = :networkId AND messageId IN (:ids)")
    suspend fun getMessagesByIds(networkId: String, ids: List<String>): List<Message>

    @Query("DELETE FROM messages WHERE timestamp < :cutoffTimestamp")
    suspend fun pruneOldMessages(cutoffTimestamp: Long): Int

    @Query("DELETE FROM messages WHERE networkId = :networkId AND messageId NOT IN (SELECT messageId FROM messages WHERE networkId = :networkId ORDER BY timestamp DESC LIMIT 2000)")
    suspend fun trimNetworkQuota(networkId: String): Int

    @Query("DELETE FROM messages WHERE networkId = :networkId")
    suspend fun clearMessagesForNetwork(networkId: String)
}

@Dao
interface NetworkMetadataDao {
    @Query("SELECT * FROM network_metadata WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveNetwork(): NetworkMetadata?

    @Query("SELECT * FROM network_metadata WHERE isActive = 1 LIMIT 1")
    fun observeActiveNetwork(): Flow<NetworkMetadata?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNetwork(network: NetworkMetadata)

    @Query("UPDATE network_metadata SET userRole = :role WHERE networkId = :networkId")
    suspend fun updateUserRole(networkId: String, role: String)

    @Query("UPDATE network_metadata SET isActive = 0 WHERE networkId = :networkId")
    suspend fun deactivateNetwork(networkId: String)

    @Query("DELETE FROM network_metadata WHERE networkId = :networkId")
    suspend fun deleteNetwork(networkId: String)
}

@Dao
interface NodeAuthorizationDao {
    @Query("SELECT * FROM node_authorizations WHERE networkId = :networkId ORDER BY approvedAt DESC")
    fun observeApprovedNodes(networkId: String): Flow<List<NodeAuthorization>>

    @Query("SELECT * FROM node_authorizations WHERE networkId = :networkId")
    suspend fun getApprovedNodes(networkId: String): List<NodeAuthorization>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuthorization(auth: NodeAuthorization)

    @Query("DELETE FROM node_authorizations WHERE nodeId = :nodeId AND networkId = :networkId")
    suspend fun revokeNode(nodeId: String, networkId: String)

    @Query("DELETE FROM node_authorizations WHERE networkId = :networkId")
    suspend fun clearAuthorizationsForNetwork(networkId: String)
}
