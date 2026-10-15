package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.model.*

class MeshTypeConverters {
    @TypeConverter
    fun fromNodeRole(role: NodeRole): String = role.name

    @TypeConverter
    fun toNodeRole(value: String): NodeRole = NodeRole.fromString(value)

    @TypeConverter
    fun fromDeliveryStatus(status: MessageDeliveryStatus): String = status.name

    @TypeConverter
    fun toDeliveryStatus(value: String): MessageDeliveryStatus =
        try {
            MessageDeliveryStatus.valueOf(value)
        } catch (_: Exception) {
            MessageDeliveryStatus.RECEIVED_FROM_NETWORK
        }
}

@Database(
    entities = [
        UserProfile::class,
        NetworkInfo::class,
        MeshMessageEntity::class,
        PendingQueueEntity::class,
        SeenMessageEntity::class,
        NodeAuthorizationEntity::class,
        MutedAuthorEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(MeshTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun networkInfoDao(): NetworkInfoDao
    abstract fun messageDao(): MessageDao
    abstract fun pendingQueueDao(): PendingQueueDao
    abstract fun seenMessageDao(): SeenMessageDao
    abstract fun nodeAuthDao(): NodeAuthDao
    abstract fun mutedAuthorDao(): MutedAuthorDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "meshboard_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
