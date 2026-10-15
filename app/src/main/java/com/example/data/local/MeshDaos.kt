package com.example.data.local

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM mesh_messages WHERE networkId = :networkId ORDER BY createdAt DESC LIMIT 2000")
    fun getMessagesForNetwork(networkId: String): Flow<List<MeshMessageEntity>>

    @Query("SELECT * FROM mesh_messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MeshMessageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MeshMessageEntity): Long

    @Query("UPDATE mesh_messages SET deliveryStatus = :status WHERE messageId = :messageId")
    suspend fun updateDeliveryStatus(messageId: String, status: MessageDeliveryStatus)

    @Query("SELECT messageId FROM mesh_messages WHERE networkId = :networkId ORDER BY createdAt DESC LIMIT 100")
    suspend fun getRecentMessageIds(networkId: String): List<String>

    @Query("SELECT * FROM mesh_messages WHERE networkId = :networkId AND messageId IN (:ids)")
    suspend fun getMessagesByIds(networkId: String, ids: List<String>): List<MeshMessageEntity>

    @Query("DELETE FROM mesh_messages WHERE createdAt < :cutoffTimestamp")
    suspend fun pruneOldMessages(cutoffTimestamp: Long): Int

    @Query("DELETE FROM mesh_messages WHERE networkId = :networkId AND messageId NOT IN (SELECT messageId FROM mesh_messages WHERE networkId = :networkId ORDER BY createdAt DESC LIMIT 2000)")
    suspend fun trimNetworkQuota(networkId: String): Int

    @Query("DELETE FROM mesh_messages WHERE networkId = :networkId")
    suspend fun clearNetworkMessages(networkId: String)
}

@Dao
interface PendingQueueDao {
    @Query("SELECT * FROM pending_queue WHERE networkId = :networkId AND expiresAt > :currentTime ORDER BY priority ASC, createdAt ASC LIMIT 100")
    suspend fun getPendingPackets(networkId: String, currentTime: Long = System.currentTimeMillis()): List<PendingQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entry: PendingQueueEntity): Long

    @Query("DELETE FROM pending_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_queue WHERE messageId = :messageId")
    suspend fun deleteByMessageId(messageId: String)

    @Query("DELETE FROM pending_queue WHERE expiresAt <= :currentTime")
    suspend fun pruneExpired(currentTime: Long = System.currentTimeMillis()): Int

    @Query("UPDATE pending_queue SET attempts = attempts + 1 WHERE id = :id")
    suspend fun incrementAttempts(id: Long)

    @Query("DELETE FROM pending_queue WHERE networkId = :networkId")
    suspend fun clearQueue(networkId: String)
}

@Dao
interface SeenMessageDao {
    @Query("SELECT COUNT(*) > 0 FROM seen_messages WHERE messageId = :messageId")
    suspend fun isSeen(messageId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSeen(entry: SeenMessageEntity)

    @Query("DELETE FROM seen_messages WHERE firstSeenAt < :cutoffTimestamp")
    suspend fun pruneOldSeen(cutoffTimestamp: Long): Int
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile LIMIT 1")
    suspend fun getProfile(): UserProfile?

    @Query("SELECT * FROM user_profile LIMIT 1")
    fun observeProfile(): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: UserProfile)
}

@Dao
interface NetworkInfoDao {
    @Query("SELECT * FROM network_info WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveNetwork(): NetworkInfo?

    @Query("SELECT * FROM network_info WHERE isActive = 1 LIMIT 1")
    fun observeActiveNetwork(): Flow<NetworkInfo?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNetwork(network: NetworkInfo)

    @Query("UPDATE network_info SET userRole = :role WHERE networkId = :networkId")
    suspend fun updateRole(networkId: String, role: NodeRole)

    @Query("UPDATE network_info SET isActive = 0 WHERE networkId = :networkId")
    suspend fun deactivateNetwork(networkId: String)

    @Query("DELETE FROM network_info WHERE networkId = :networkId")
    suspend fun deleteNetwork(networkId: String)
}

@Dao
interface NodeAuthDao {
    @Query("SELECT * FROM node_authorizations WHERE networkId = :networkId ORDER BY approvedAt DESC")
    fun observeApprovedNodes(networkId: String): Flow<List<NodeAuthorizationEntity>>

    @Query("SELECT * FROM node_authorizations WHERE networkId = :networkId")
    suspend fun getApprovedNodes(networkId: String): List<NodeAuthorizationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodeAuth(auth: NodeAuthorizationEntity)

    @Query("DELETE FROM node_authorizations WHERE nodeId = :nodeId AND networkId = :networkId")
    suspend fun revokeNode(nodeId: String, networkId: String)
}
