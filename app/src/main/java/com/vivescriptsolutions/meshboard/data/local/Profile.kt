package com.vivescriptsolutions.meshboard.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local device identity and profile entity.
 * Persists user display name and immutable profileId across restarts without requiring
 * external cloud accounts, email, phone numbers, or passwords.
 */
@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey
    val profileId: String,
    val displayName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val identityFingerprint: String? = null
)
