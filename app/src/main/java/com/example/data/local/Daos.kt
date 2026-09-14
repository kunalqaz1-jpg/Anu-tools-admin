package com.example.data.local

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY updatedAt DESC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY updatedAt DESC")
    suspend fun getAllProductsDirect(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE active = 1 ORDER BY name ASC")
    fun getActiveProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE productId = :id")
    fun getProductById(id: String): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE productId = :id")
    suspend fun getProductByIdDirect(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE sku = :sku LIMIT 1")
    suspend fun getProductBySku(sku: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Update
    suspend fun update(product: ProductEntity)

    @Delete
    suspend fun delete(product: ProductEntity)

    @Query("DELETE FROM products WHERE productId = :productId")
    suspend fun deleteById(productId: String)

    @Query("DELETE FROM products WHERE productId IN (:productIds)")
    suspend fun deleteByIds(productIds: List<String>)

    @Query("UPDATE products SET stockQuantity = :quantity, stockStatus = :status, updatedAt = :updatedAt WHERE productId = :productId")
    suspend fun updateStock(productId: String, quantity: Int, status: StockStatus, updatedAt: Long)

    @Query("UPDATE products SET active = :active, updatedAt = :updatedAt WHERE productId = :productId")
    suspend fun setProductActive(productId: String, active: Boolean, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM products WHERE active = 1")
    fun getActiveProductsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM products WHERE stockStatus = 'LOW_STOCK' AND active = 1")
    fun getLowStockCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM products WHERE stockStatus = 'OUT_OF_STOCK' AND active = 1")
    fun getOutOfStockCount(): Flow<Int>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllCategoriesDirect(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)
}

@Dao
interface BrandDao {
    @Query("SELECT * FROM brands ORDER BY sortOrder ASC, name ASC")
    fun getAllBrands(): Flow<List<BrandEntity>>

    @Query("SELECT * FROM brands ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllBrandsDirect(): List<BrandEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(brand: BrandEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(brands: List<BrandEntity>)

    @Update
    suspend fun update(brand: BrandEntity)

    @Delete
    suspend fun delete(brand: BrandEntity)
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders ORDER BY createdAt DESC")
    fun getAllOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE orderId = :orderId")
    fun getOrderById(orderId: String): Flow<OrderEntity?>

    @Query("SELECT * FROM orders WHERE orderId = :orderId")
    suspend fun getOrderByIdDirect(orderId: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE retailerId = :retailerId ORDER BY createdAt DESC")
    fun getOrdersForRetailer(retailerId: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE retailerId = :retailerId ORDER BY createdAt DESC")
    suspend fun getOrdersForRetailerDirect(retailerId: String): List<OrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(order: OrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(orders: List<OrderEntity>)

    @Update
    suspend fun update(order: OrderEntity)

    @Query("DELETE FROM orders WHERE orderId IN ('ord_001', 'ord_002', 'ord_003')")
    suspend fun deleteLegacyMockOrders()

    @Query("DELETE FROM orders WHERE orderId NOT IN (:activeIds)")
    suspend fun pruneDeletedOrders(activeIds: List<String>)

    @Query("DELETE FROM orders")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM orders WHERE orderStatus = :status")
    fun getOrdersCountByStatus(status: OrderStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM orders")
    fun getTotalOrdersCount(): Flow<Int>
}

@Dao
interface InventoryTransactionDao {
    @Query("SELECT * FROM inventory_transactions ORDER BY createdAt DESC")
    fun getAllTransactions(): Flow<List<InventoryTransactionEntity>>

    @Query("SELECT * FROM inventory_transactions WHERE productId = :productId ORDER BY createdAt DESC")
    fun getTransactionsForProduct(productId: String): Flow<List<InventoryTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: InventoryTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<InventoryTransactionEntity>)
}

@Dao
interface RetailerDao {
    @Query("SELECT * FROM retailers ORDER BY totalBusinessValue DESC")
    fun getAllRetailers(): Flow<List<RetailerEntity>>

    @Query("SELECT * FROM retailers WHERE retailerId = :id")
    fun getRetailerById(id: String): Flow<RetailerEntity?>

    @Query("SELECT * FROM retailers WHERE retailerId = :id LIMIT 1")
    suspend fun getRetailerByIdDirect(id: String): RetailerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(retailer: RetailerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(retailers: List<RetailerEntity>)

    @Update
    suspend fun update(retailer: RetailerEntity)

    @Query("DELETE FROM retailers WHERE retailerId NOT IN (:activeIds)")
    suspend fun pruneDeletedRetailers(activeIds: List<String>)

    @Query("DELETE FROM retailers")
    suspend fun deleteAll()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun getAllNotifications(): Flow<List<NotificationItemEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE read = 0")
    fun getUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notifications: List<NotificationItemEntity>)

    @Query("UPDATE notifications SET read = 1 WHERE notificationId = :id")
    suspend fun markAsRead(id: String)

    @Query("UPDATE notifications SET read = 1")
    suspend fun markAllAsRead()
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAllAuditLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AuditLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<AuditLogEntity>)
}

@Dao
interface BusinessSettingsDao {
    @Query("SELECT * FROM business_settings WHERE settingsId = 'default_settings' LIMIT 1")
    fun getSettings(): Flow<BusinessSettingsEntity?>

    @Query("SELECT * FROM business_settings WHERE settingsId = 'default_settings' LIMIT 1")
    suspend fun getSettingsDirect(): BusinessSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: BusinessSettingsEntity)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY name ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(users: List<UserEntity>)
}
