package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.NotificationType
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentStatus
import com.example.data.model.RetailerStatus
import com.example.data.model.StockStatus
import com.example.data.model.TransactionType
import com.example.data.model.UserRole

class Converters {
    @TypeConverter
    fun fromUserRole(role: UserRole?): String = role?.name ?: UserRole.STAFF.name

    @TypeConverter
    fun toUserRole(value: String?): UserRole = try {
        UserRole.valueOf(value ?: UserRole.STAFF.name)
    } catch (e: Exception) {
        UserRole.STAFF
    }

    @TypeConverter
    fun fromStockStatus(status: StockStatus?): String = status?.name ?: StockStatus.IN_STOCK.name

    @TypeConverter
    fun toStockStatus(value: String?): StockStatus = try {
        StockStatus.valueOf(value ?: StockStatus.IN_STOCK.name)
    } catch (e: Exception) {
        StockStatus.IN_STOCK
    }

    @TypeConverter
    fun fromOrderStatus(status: OrderStatus?): String = status?.name ?: OrderStatus.PENDING.name

    @TypeConverter
    fun toOrderStatus(value: String?): OrderStatus = OrderStatus.fromString(value)

    @TypeConverter
    fun fromPaymentMethod(method: PaymentMethod?): String = method?.name ?: PaymentMethod.COD.name

    @TypeConverter
    fun toPaymentMethod(value: String?): PaymentMethod = PaymentMethod.fromString(value)

    @TypeConverter
    fun fromPaymentStatus(status: PaymentStatus?): String = status?.name ?: PaymentStatus.UNPAID.name

    @TypeConverter
    fun toPaymentStatus(value: String?): PaymentStatus = PaymentStatus.fromString(value)

    @TypeConverter
    fun fromTransactionType(type: TransactionType?): String = type?.name ?: TransactionType.MANUAL_ADJUSTMENT.name

    @TypeConverter
    fun toTransactionType(value: String?): TransactionType = try {
        TransactionType.valueOf(value ?: TransactionType.MANUAL_ADJUSTMENT.name)
    } catch (e: Exception) {
        TransactionType.MANUAL_ADJUSTMENT
    }

    @TypeConverter
    fun fromRetailerStatus(status: RetailerStatus?): String = status?.name ?: RetailerStatus.ACTIVE.name

    @TypeConverter
    fun toRetailerStatus(value: String?): RetailerStatus = try {
        RetailerStatus.valueOf(value ?: RetailerStatus.ACTIVE.name)
    } catch (e: Exception) {
        RetailerStatus.ACTIVE
    }

    @TypeConverter
    fun fromNotificationType(type: NotificationType?): String = type?.name ?: NotificationType.NEW_ORDER.name

    @TypeConverter
    fun toNotificationType(value: String?): NotificationType = try {
        NotificationType.valueOf(value ?: NotificationType.NEW_ORDER.name)
    } catch (e: Exception) {
        NotificationType.NEW_ORDER
    }
}
