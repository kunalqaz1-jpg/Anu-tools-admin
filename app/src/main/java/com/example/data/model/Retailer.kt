package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RetailerStatus(val displayName: String) {
    ACTIVE("Active"),
    INACTIVE("Inactive"),
    BLOCKED("Blocked"),
    PENDING("Pending Verification");

    companion object {
        fun fromString(str: String?): RetailerStatus {
            if (str == null) return ACTIVE
            val upper = str.trim().uppercase()
            return when (upper) {
                "ACTIVE", "APPROVED", "VERIFIED", "TRUE" -> ACTIVE
                "INACTIVE", "FALSE", "DISABLED" -> INACTIVE
                "BLOCKED", "SUSPENDED" -> BLOCKED
                "PENDING", "PENDING_VERIFICATION", "NEW" -> PENDING
                else -> try {
                    RetailerStatus.valueOf(upper)
                } catch (_: Exception) {
                    ACTIVE
                }
            }
        }
    }
}

@Entity(tableName = "retailers")
data class RetailerEntity(
    @PrimaryKey val retailerId: String,
    val name: String, // Owner name
    val shopName: String,
    val phone: String,
    val email: String = "",
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val pincode: String = "",
    val profilePhotoUrl: String = "",
    val totalOrders: Int = 0,
    val totalBusinessValue: Double = 0.0,
    val status: RetailerStatus = RetailerStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
