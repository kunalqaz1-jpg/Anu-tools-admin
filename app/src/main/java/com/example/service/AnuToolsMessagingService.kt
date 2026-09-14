package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AnuToolsDatabase
import com.example.data.model.NotificationItemEntity
import com.example.data.model.NotificationType
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class AnuToolsMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Store FCM token to Firestore so that backend / Cloud Functions can target admin devices
        try {
            val db = FirebaseFirestore.getInstance()
            val tokenData = hashMapOf(
                "token" to token,
                "role" to "ADMIN",
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("admin_fcm_tokens").document(token).set(tokenData)
        } catch (_: Exception) {
            // Non-blocking in case of offline initialization
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "New Admin Alert"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: remoteMessage.data["message"]
            ?: "An update requires your attention."
        val orderId = remoteMessage.data["orderId"] ?: ""
        val typeStr = remoteMessage.data["type"] ?: "NEW_ORDER"

        val notificationType = when (typeStr.uppercase()) {
            "LOW_STOCK" -> NotificationType.LOW_STOCK
            "OUT_OF_STOCK" -> NotificationType.OUT_OF_STOCK
            "ORDER_CANCELLED" -> NotificationType.ORDER_CANCELLED
            "PAYMENT_SUBMITTED" -> NotificationType.PAYMENT_SUBMITTED
            else -> NotificationType.NEW_ORDER
        }

        // 1. Save to local database so it displays in Notifications screen
        serviceScope.launch {
            try {
                val db = AnuToolsDatabase.getInstance(applicationContext)
                val entity = NotificationItemEntity(
                    notificationId = "notif_${UUID.randomUUID().toString().take(8)}",
                    type = notificationType,
                    title = title,
                    message = body,
                    orderId = orderId,
                    recipientId = "admin",
                    read = false,
                    createdAt = System.currentTimeMillis()
                )
                db.notificationDao().insert(entity)
            } catch (_: Exception) {
                // Safe ignore if DB insert fails
            }
        }

        // 2. Show System Notification
        showSystemNotification(title, body, orderId)
    }

    private fun showSystemNotification(title: String, body: String, orderId: String) {
        val channelId = "anu_tools_admin_orders"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Anu Tools Orders & Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time alerts for incoming retailer orders and inventory"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (orderId.isNotBlank()) {
                putExtra("orderId", orderId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
