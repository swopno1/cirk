package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.data.model.MeshMessageEntity
import com.example.data.model.NodeRole
import java.util.concurrent.ConcurrentHashMap

class MeshNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_MESSAGES = "meshboard_messages_channel"
        const val CHANNEL_SERVICE = "meshboard_service_channel"
        private const val GROUP_MESSAGES = "meshboard_messages_group"
    }

    private val notifiedMessageIds = ConcurrentHashMap.newKeySet<String>()

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "CIRK Announcements",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Public announcements from nearby CIRK mesh members"
                enableVibration(true)
            }

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "CIRK Mesh Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "CIRK background peer connection and relay service"
            }

            notificationManager.createNotificationChannel(messagesChannel)
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    fun showMessageNotification(message: MeshMessageEntity) {
        // Section 14: Deduplication - Never trigger twice for the same messageId
        if (!notifiedMessageIds.add(message.messageId)) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            message.messageId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val roleBadge = when (message.senderRole) {
            NodeRole.HOST -> "[Host]"
            NodeRole.RELAY_NODE -> "[Relay]"
            NodeRole.CLIENT -> "[Client]"
        }

        val title = "$roleBadge ${message.senderDisplayName}"
        val textPreview = if (message.text.length > 120) message.text.take(120) + "…" else message.text

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(textPreview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_MESSAGES)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(message.messageId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Notifications permission not granted
        }
    }
}
