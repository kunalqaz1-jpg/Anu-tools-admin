package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val logId: String,
    val action: String, // PRODUCT_CREATED, PRODUCT_UPDATED, PRODUCT_ARCHIVED, STOCK_CHANGED, PRICE_CHANGED, ORDER_STATUS_CHANGED, PAYMENT_STATUS_CHANGED, SETTING_UPDATED
    val userId: String,
    val userName: String,
    val entityType: String,
    val entityId: String,
    val oldValue: String = "",
    val newValue: String = "",
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
