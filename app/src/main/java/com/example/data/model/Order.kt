package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class OrderStatus(val displayName: String, val firestoreCode: String) {
    PENDING("Order Placed", "ORDER_PLACED"),
    CONFIRMED("Order Confirmed", "ORDER_CONFIRMED"),
    PACKING("Packing", "PACKING"),
    READY_FOR_DISPATCH("Ready for Dispatch", "READY_FOR_DISPATCH"),
    OUT_FOR_DELIVERY("Out for Delivery", "OUT_FOR_DELIVERY"),
    DELIVERED("Order Completed", "ORDER_COMPLETED"),
    CANCELLED("Cancelled", "CANCELLED");

    companion object {
        fun fromString(str: String?): OrderStatus {
            if (str == null) return PENDING
            val upper = str.trim().uppercase()
            return when (upper) {
                "ORDER_PLACED", "PENDING", "PLACED", "NEW" -> PENDING
                "ORDER_CONFIRMED", "CONFIRMED", "ACCEPTED" -> CONFIRMED
                "PACKING", "IN_PACKING", "PACKED" -> PACKING
                "READY_FOR_DISPATCH", "DISPATCH_READY", "READY" -> READY_FOR_DISPATCH
                "OUT_FOR_DELIVERY", "DISPATCHED", "SHIPPED", "IN_TRANSIT" -> OUT_FOR_DELIVERY
                "ORDER_COMPLETED", "COMPLETED", "DELIVERED", "DONE" -> DELIVERED
                "CANCELLED", "CANCELED" -> CANCELLED
                else -> try {
                    OrderStatus.valueOf(upper)
                } catch (_: Exception) {
                    PENDING
                }
            }
        }
    }
}

enum class PaymentMethod(val displayName: String) {
    COD("Cash on Delivery (COD)"),
    QR_PAYMENT("QR / Scanner Payment");

    companion object {
        fun fromString(str: String?): PaymentMethod {
            if (str == null) return COD
            val upper = str.trim().uppercase()
            return when {
                upper.contains("QR") || upper.contains("SCAN") || upper.contains("UPI") || upper.contains("ONLINE") -> QR_PAYMENT
                else -> COD
            }
        }
    }
}

enum class PaymentStatus(val displayName: String, val firestoreCode: String) {
    UNPAID("Unpaid", "UNPAID"),
    PAYMENT_PENDING_VERIFICATION("Pending", "PENDING"),
    PAID("Paid", "PAID"),
    PARTIALLY_PAID("Partially Paid", "PARTIALLY_PAID"),
    REFUNDED("Refunded", "REFUNDED");

    companion object {
        fun fromString(str: String?): PaymentStatus {
            if (str == null) return UNPAID
            val upper = str.trim().uppercase()
            return when (upper) {
                "PAID", "SUCCESS", "COMPLETED" -> PAID
                "PENDING", "PAYMENT_PENDING_VERIFICATION", "VERIFICATION_PENDING", "PENDING_VERIFICATION" -> PAYMENT_PENDING_VERIFICATION
                "UNPAID", "NOT_PAID", "FAILED" -> UNPAID
                "PARTIALLY_PAID", "PARTIAL" -> PARTIALLY_PAID
                "REFUNDED" -> REFUNDED
                else -> try {
                    PaymentStatus.valueOf(upper)
                } catch (_: Exception) {
                    UNPAID
                }
            }
        }
    }
}

data class OrderItemSnapshot(
    val productId: String,
    val sku: String,
    val name: String,
    val imageUrl: String = "",
    val quantity: Int,
    val unit: String = "Piece",
    val unitPrice: Double,
    val mrp: Double,
    val gstPercentage: Double = 18.0,
    val gstAmount: Double = 0.0,
    val subtotal: Double
)

data class StatusHistoryItem(
    val status: OrderStatus,
    val changedAt: Long,
    val changedBy: String,
    val notes: String = ""
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val orderId: String,
    val orderNumber: String,
    val retailerId: String,
    val retailerName: String,
    val shopName: String,
    val phone: String,
    val alternatePhone: String = "",
    val address: String,
    val city: String,
    val pincode: String,
    val itemsJson: String,
    val itemsCount: Int,
    val subtotal: Double,
    val discount: Double = 0.0,
    val gst: Double,
    val deliveryCharge: Double = 0.0,
    val total: Double,
    val paymentMethod: PaymentMethod = PaymentMethod.COD,
    val paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
    val amountPaid: Double = 0.0,
    val paymentReference: String = "",
    val paymentNote: String = "",
    val paymentVerifiedBy: String = "",
    val paymentVerifiedAt: Long? = null,
    val orderStatus: OrderStatus = OrderStatus.PENDING,
    val internalNote: String = "",
    val customerNote: String = "",
    val statusHistoryJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val confirmedAt: Long? = null,
    val dispatchedAt: Long? = null,
    val deliveredAt: Long? = null,
    val cancelledAt: Long? = null
)
