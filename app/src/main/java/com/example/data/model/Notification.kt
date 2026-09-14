package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class NotificationType(val titleDefault: String) {
    NEW_ORDER("New Order Received"),
    LOW_STOCK("Low Stock Warning"),
    OUT_OF_STOCK("Out of Stock Alert"),
    ORDER_CANCELLED("Order Cancelled"),
    PAYMENT_SUBMITTED("Payment Submitted")
}

@Entity(tableName = "notifications")
data class NotificationItemEntity(
    @PrimaryKey val notificationId: String,
    val type: NotificationType,
    val title: String,
    val message: String,
    val orderId: String = "",
    val productId: String = "",
    val recipientId: String = "admin",
    val read: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
