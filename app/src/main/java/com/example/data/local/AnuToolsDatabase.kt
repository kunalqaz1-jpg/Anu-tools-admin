package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.model.*

@Database(
    entities = [
        ProductEntity::class,
        CategoryEntity::class,
        BrandEntity::class,
        OrderEntity::class,
        InventoryTransactionEntity::class,
        RetailerEntity::class,
        NotificationItemEntity::class,
        AuditLogEntity::class,
        BusinessSettingsEntity::class,
        UserEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AnuToolsDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun categoryDao(): CategoryDao
    abstract fun brandDao(): BrandDao
    abstract fun orderDao(): OrderDao
    abstract fun inventoryTransactionDao(): InventoryTransactionDao
    abstract fun retailerDao(): RetailerDao
    abstract fun notificationDao(): NotificationDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun businessSettingsDao(): BusinessSettingsDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: AnuToolsDatabase? = null

        fun getInstance(context: Context): AnuToolsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AnuToolsDatabase::class.java,
                    "anu_tools_admin.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
