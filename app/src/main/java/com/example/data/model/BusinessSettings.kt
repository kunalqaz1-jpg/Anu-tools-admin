package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "business_settings")
data class BusinessSettingsEntity(
    @PrimaryKey val settingsId: String = "default_settings",
    val businessName: String = "Anu Tools & Service Center",
    val phone: String = "+91 98765 43210",
    val whatsappNumber: String = "+91 98765 43210",
    val email: String = "contact@anutools.com",
    val address: String = "Plot 42, Industrial Area, Phase II, Near Tool Market",
    val city: String = "Ludhiana",
    val state: String = "Punjab",
    val pincode: String = "141003",
    val gstNumber: String = "03AABCU9603R1ZM",
    val logoUrl: String = "",
    val codEnabled: Boolean = true,
    val qrPaymentEnabled: Boolean = true,
    val qrImageUrl: String = "",
    val upiId: String = "anutools@okaxis",
    val paymentInstructions: String = "Scan UPI QR code or pay to UPI ID. Enter Order Number in payment remarks for immediate verification.",
    val defaultDeliveryCharge: Double = 150.0,
    val minimumOrderAmount: Double = 1000.0,
    val maximumOrderAmount: Double = 500000.0,
    val allowOutOfStockOrders: Boolean = false,
    val newOrderNotification: Boolean = true,
    val lowStockNotification: Boolean = true,
    val paymentNotification: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val deliveryFee: Double get() = defaultDeliveryCharge
    val upiEnabled: Boolean get() = qrPaymentEnabled
    val gstin: String get() = gstNumber
    val shopAddress: String get() = address
}
