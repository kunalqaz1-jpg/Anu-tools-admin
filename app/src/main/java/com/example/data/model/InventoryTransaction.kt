package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransactionType(val displayName: String) {
    STOCK_ADDED("Stock Added"),
    STOCK_REMOVED("Stock Removed"),
    ORDER_CONFIRMED("Order Confirmed"),
    ORDER_CANCELLED("Order Cancelled Restocked"),
    MANUAL_ADJUSTMENT("Manual Adjustment"),
    RETURN("Customer Return")
}

@Entity(tableName = "inventory_transactions")
data class InventoryTransactionEntity(
    @PrimaryKey val transactionId: String,
    val productId: String,
    val productName: String,
    val sku: String,
    val type: TransactionType,
    val quantity: Int,
    val previousStock: Int,
    val newStock: Int,
    val reason: String = "",
    val orderId: String = "",
    val createdBy: String = "admin",
    val createdAt: Long = System.currentTimeMillis()
)
