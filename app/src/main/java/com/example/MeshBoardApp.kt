package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.model.UserProfile
import com.example.mesh.routing.MeshRouter
import com.example.mesh.transport.NearbyConnectionsTransport
import com.example.notification.MeshNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class MeshBoardApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var notificationManager: MeshNotificationManager
        private set

    var meshRouter: MeshRouter? = null
        private set

    private val applicationScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        notificationManager = MeshNotificationManager(this)

        val transport = NearbyConnectionsTransport(this)
        meshRouter = MeshRouter(
            transport = transport,
            database = database,
            onNewMessageDelivered = { message ->
                notificationManager.showMessageNotification(message)
            }
        )

        // Ensure default user profile exists
        applicationScope.launch {
            val existing = database.userProfileDao().getProfile()
            if (existing == null) {
                val newProfile = UserProfile(
                    profileId = UUID.randomUUID().toString(),
                    displayName = "Node-" + UUID.randomUUID().toString().take(4).uppercase()
                )
                database.userProfileDao().saveProfile(newProfile)
            }
        }
    }
}
