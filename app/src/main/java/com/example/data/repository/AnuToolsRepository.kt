package com.example.data.repository

import com.example.data.local.AnuToolsDatabase
import com.example.data.model.*
import com.example.data.remote.FirestoreCatalogRepository
import com.example.data.remote.FirestoreOrderRepository
import com.example.data.remote.FirestoreProductRepository
import com.example.data.remote.FirestoreRetailerRepository
import com.example.data.remote.FirestoreSettingsRepository
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AnuToolsRepository(private val db: AnuToolsDatabase) {

    private val firestoreProductRepository = FirestoreProductRepository()
    private val firestoreCatalogRepository = FirestoreCatalogRepository()
    private val firestoreOrderRepository = FirestoreOrderRepository()
    private val firestoreRetailerRepository = FirestoreRetailerRepository()
    private val firestoreSettingsRepository = FirestoreSettingsRepository()

    // Products are sourced from Firebase Firestore in real-time with automatic local Room cache fallback
    val products: Flow<List<ProductEntity>> = combine(
        firestoreProductRepository.productsFlow,
        db.productDao().getAllProducts()
    ) { firestoreList, localList ->
        if (firestoreList.isNotEmpty()) {
            try {
                db.productDao().insertAll(firestoreList)
            } catch (_: Exception) {}
            firestoreList
        } else {
            localList
        }
    }
    val activeProducts: Flow<List<ProductEntity>> = products.map { list ->
        list.filter { it.active }
    }

    // Categories & Brands: Firestore real-time (so retailer/customer app sees changes instantly)
    // with automatic local Room cache fallback when offline.
    val categories: Flow<List<CategoryEntity>> = combine(
        firestoreCatalogRepository.categoriesFlow,
        db.categoryDao().getAllCategories()
    ) { firestoreList, localList ->
        if (firestoreList.isNotEmpty()) {
            try { db.categoryDao().insertAll(firestoreList) } catch (_: Exception) {}
            firestoreList
        } else {
            localList
        }
    }

    val brands: Flow<List<BrandEntity>> = combine(
        firestoreCatalogRepository.brandsFlow,
        db.brandDao().getAllBrands()
    ) { firestoreList, localList ->
        if (firestoreList.isNotEmpty()) {
            try { db.brandDao().insertAll(firestoreList) } catch (_: Exception) {}
            firestoreList
        } else {
            localList
        }
    }

    // Orders: Live real-time Firestore listener connecting directly to collection "orders" (anu-tools-production).
    // Automatically receives orders placed by the retailer app and caches them locally for instant rendering.
    val orders: Flow<List<OrderEntity>> = combine(
        firestoreOrderRepository.ordersFlow,
        db.orderDao().getAllOrders()
    ) { firestoreList, localList ->
        if (firestoreList.isNotEmpty()) {
            try {
                db.orderDao().deleteLegacyMockOrders()
                db.orderDao().insertAll(firestoreList)
                val activeIds = firestoreList.map { it.orderId }
                db.orderDao().pruneDeletedOrders(activeIds)
            } catch (_: Exception) {}
            firestoreList
        } else {
            localList.filterNot { it.orderId in listOf("ord_001", "ord_002", "ord_003", "ord_004") }
        }
    }

    // Retailers: Live real-time Firestore listener connecting directly to collection "retailers" (anu-tools-production).
    // Displays real registered and verified retailers, with profile photo and full address.
    val retailers: Flow<List<RetailerEntity>> = combine(
        firestoreRetailerRepository.retailersFlow,
        db.retailerDao().getAllRetailers()
    ) { firestoreList, localList ->
        if (firestoreList.isNotEmpty()) {
            try {
                db.retailerDao().insertAll(firestoreList)
                val activeIds = firestoreList.map { it.retailerId }
                db.retailerDao().pruneDeletedRetailers(activeIds)
            } catch (_: Exception) {}
            firestoreList
        } else {
            localList.filterNot { it.retailerId in listOf("ret_01", "ret_02", "ret_03", "ret_04") }
        }
    }

    val inventoryTransactions: Flow<List<InventoryTransactionEntity>> = db.inventoryTransactionDao().getAllTransactions()
    val notifications: Flow<List<NotificationItemEntity>> = db.notificationDao().getAllNotifications()
    val unreadNotificationsCount: Flow<Int> = db.notificationDao().getUnreadCount()
    val auditLogs: Flow<List<AuditLogEntity>> = db.auditLogDao().getAllAuditLogs()

    // Business Settings: Live real-time sync with businessSettings collection shared with Retailer App
    val businessSettings: Flow<BusinessSettingsEntity?> = combine(
        firestoreSettingsRepository.settingsFlow,
        db.businessSettingsDao().getSettings()
    ) { remote, local ->
        if (remote != null) {
            try {
                db.businessSettingsDao().insertOrUpdate(remote)
            } catch (_: Exception) {}
            remote
        } else {
            local
        }
    }

    val users: Flow<List<UserEntity>> = db.userDao().getAllUsers()

    fun getProduct(id: String): Flow<ProductEntity?> = combine(
        firestoreProductRepository.getProductFlow(id),
        db.productDao().getProductById(id)
    ) { remote, local ->
        remote ?: local
    }

    fun getOrder(id: String): Flow<OrderEntity?> = combine(
        firestoreOrderRepository.getOrderFlow(id),
        db.orderDao().getOrderById(id)
    ) { remote, local ->
        remote ?: local
    }

    fun getRetailer(id: String): Flow<RetailerEntity?> = combine(
        firestoreRetailerRepository.getRetailerFlow(id),
        db.retailerDao().getRetailerById(id)
    ) { remote, local ->
        remote ?: local
    }

    fun getOrdersForRetailer(retailerId: String): Flow<List<OrderEntity>> =
        db.orderDao().getOrdersForRetailer(retailerId)

    suspend fun seedFirestoreProductsIfEmpty(sampleProducts: List<ProductEntity>) = withContext(Dispatchers.IO) {
        firestoreProductRepository.seedInitialProductsIfEmpty(sampleProducts)
    }

    /** Seed default categories and brands to Firestore if the collections are empty (run once on first admin launch). */
    suspend fun seedCatalogToFirestoreIfEmpty(
        defaultCategories: List<CategoryEntity>,
        defaultBrands: List<BrandEntity>
    ) = withContext(Dispatchers.IO) {
        firestoreCatalogRepository.seedCategoriesIfEmpty(defaultCategories)
        firestoreCatalogRepository.seedBrandsIfEmpty(defaultBrands)
    }

    suspend fun saveProduct(product: ProductEntity, isNew: Boolean, adminName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val calculatedStatus = ProductEntity.calculateStockStatus(product.stockQuantity, product.minimumStockLevel)
        val finalProduct = product.copy(
            stockStatus = calculatedStatus,
            updatedAt = System.currentTimeMillis()
        )
        // Primary operation in Firestore
        val firestoreResult = firestoreProductRepository.saveProduct(finalProduct, isNew, adminName)
        if (firestoreResult.isFailure) {
            return@withContext firestoreResult
        }

        // Only update local Room database after Firestore succeeds
        try {
            if (isNew) {
                db.productDao().insert(finalProduct)
                db.auditLogDao().insert(
                    AuditLogEntity(
                        logId = "log_${UUID.randomUUID()}",
                        action = "PRODUCT_CREATED",
                        userId = adminName,
                        userName = adminName,
                        entityType = "PRODUCT",
                        entityId = finalProduct.productId,
                        details = "Created product: ${finalProduct.name} (SKU: ${finalProduct.sku})"
                    )
                )
                if (finalProduct.stockQuantity > 0) {
                    db.inventoryTransactionDao().insert(
                        InventoryTransactionEntity(
                            transactionId = "tx_${UUID.randomUUID()}",
                            productId = finalProduct.productId,
                            productName = finalProduct.name,
                            sku = finalProduct.sku,
                            type = TransactionType.STOCK_ADDED,
                            quantity = finalProduct.stockQuantity,
                            previousStock = 0,
                            newStock = finalProduct.stockQuantity,
                            reason = "Initial inventory when adding product",
                            createdBy = adminName
                        )
                    )
                }
            } else {
                val oldProduct = db.productDao().getProductByIdDirect(product.productId)
                db.productDao().insert(finalProduct)
                db.auditLogDao().insert(
                    AuditLogEntity(
                        logId = "log_${UUID.randomUUID()}",
                        action = "PRODUCT_UPDATED",
                        userId = adminName,
                        userName = adminName,
                        entityType = "PRODUCT",
                        entityId = finalProduct.productId,
                        oldValue = "Stock: ${oldProduct?.stockQuantity}, Price: ₹${oldProduct?.sellingPrice}",
                        newValue = "Stock: ${finalProduct.stockQuantity}, Price: ₹${finalProduct.sellingPrice}",
                        details = "Updated details for ${finalProduct.name}"
                    )
                )
            }
        } catch (_: Exception) {}

        Result.success(Unit)
    }

    /**
     * Complete Create/Edit transaction including Firebase Storage image uploads,
     * deletions of removed images, Firestore document write, and local Room cache update.
     */
    suspend fun saveProductWithImages(
        product: ProductEntity,
        isNew: Boolean,
        newLocalImageUris: List<android.net.Uri>,
        existingRemoteUrls: List<String>,
        removedRemoteUrls: List<String>,
        selectedThumbnail: String?,
        adminName: String,
        context: android.content.Context
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetProductId = if (isNew && product.productId.isBlank()) {
            "prod_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        } else {
            product.productId
        }

        // 1 & 2. Compress and Upload newly selected local images to Cloudinary
        val uploadedUrls = mutableListOf<String>()
        var resolvedThumbnailUrl: String? = null

        for ((index, uri) in newLocalImageUris.withIndex()) {
            android.util.Log.d("AnuToolsRepository", "IMAGE_COMPRESSION -> Compressing local image $uri for product $targetProductId")
            val bytes = com.example.util.ImageCompressor.compressImage(context, uri)
            if (bytes == null || bytes.isEmpty()) {
                val err = Exception("Could not process or compress selected image. Please choose a valid image.")
                android.util.Log.e("AnuToolsRepository", "IMAGE_COMPRESS_FAILURE -> $err")
                return@withContext Result.failure(err)
            }

            val filename = "prod_${targetProductId}_${System.currentTimeMillis()}_$index.jpg"
            val uploadResult = com.example.util.CloudinaryUploader.uploadImage(
                imageBytes = bytes,
                filename = filename,
                folder = "products/$targetProductId"
            )

            if (uploadResult.isFailure) {
                val err = uploadResult.exceptionOrNull()
                    ?: Exception("Cloudinary upload failed for product image.")
                android.util.Log.e("AnuToolsRepository", "CLOUDINARY_UPLOAD_FAILURE -> ${err.message}", err)
                // If Cloudinary upload fails, show an upload error and do not update Firestore.
                return@withContext Result.failure(err)
            }

            val secureUrl = uploadResult.getOrThrow()
            if (!secureUrl.startsWith("https://")) {
                val err = Exception("Upload response did not return a valid HTTPS Cloudinary URL: $secureUrl")
                android.util.Log.e("AnuToolsRepository", "INVALID_CLOUDINARY_URL -> $secureUrl")
                return@withContext Result.failure(err)
            }

            android.util.Log.d("AnuToolsRepository", "CLOUDINARY_UPLOAD_SUCCESS -> $secureUrl")
            uploadedUrls.add(secureUrl)

            if (selectedThumbnail == uri.toString()) {
                resolvedThumbnailUrl = secureUrl
            }
        }

        // 3. Assemble final image list
        // Filter out any invalid URIs (content://, file://, data:) to guarantee strictly HTTPS remote URLs in Firestore
        val cleanExistingRemoteUrls = existingRemoteUrls.filter { url ->
            (url.startsWith("https://") || url.startsWith("http://")) &&
            !url.startsWith("data:") &&
            !url.startsWith("content:") &&
            !url.startsWith("file:")
        }

        val finalImageList = cleanExistingRemoteUrls + uploadedUrls
        val finalThumbnail = when {
            resolvedThumbnailUrl != null -> resolvedThumbnailUrl
            selectedThumbnail != null && cleanExistingRemoteUrls.contains(selectedThumbnail) -> selectedThumbnail
            finalImageList.isNotEmpty() -> finalImageList.first()
            else -> ""
        }

        // Guard: Guarantee no Base64 or local URIs can ever be written to Firestore
        for (img in finalImageList) {
            if (img.startsWith("data:") || img.startsWith("content:") || img.startsWith("file:")) {
                return@withContext Result.failure(Exception("Cannot save invalid image format to Firestore: $img"))
            }
        }
        if (finalThumbnail.startsWith("data:") || finalThumbnail.startsWith("content:") || finalThumbnail.startsWith("file:")) {
            return@withContext Result.failure(Exception("Cannot save invalid thumbnail format to Firestore: $finalThumbnail"))
        }

        val calculatedStatus = ProductEntity.calculateStockStatus(product.stockQuantity, product.minimumStockLevel)
        val now = System.currentTimeMillis()
        val finalProduct = product.copy(
            productId = targetProductId,
            stockStatus = calculatedStatus,
            imagesJson = JSONArray(finalImageList).toString(),
            thumbnailUrl = finalThumbnail,
            updatedAt = now,
            updatedBy = adminName,
            createdBy = if (isNew) adminName else product.createdBy
        )

        // 5. Primary operation in Firestore
        android.util.Log.d("AnuToolsRepository", "FIRESTORE_UPDATE_START -> Updating products/$targetProductId in Firestore")
        val firestoreResult = firestoreProductRepository.saveProduct(finalProduct, isNew, adminName)
        if (firestoreResult.isFailure) {
            android.util.Log.e("AnuToolsRepository", "FIRESTORE_UPDATE_FAILURE -> Failed to update product $targetProductId in Firestore", firestoreResult.exceptionOrNull())
            return@withContext firestoreResult
        }
        android.util.Log.d("AnuToolsRepository", "FIRESTORE_UPDATE_SUCCESS -> Successfully updated product $targetProductId in Firestore")

        // 6. Update local Room database ONLY after Firestore succeeds
        try {
            if (isNew) {
                db.productDao().insert(finalProduct)
                db.auditLogDao().insert(
                    AuditLogEntity(
                        logId = "log_${UUID.randomUUID()}",
                        action = "PRODUCT_CREATED",
                        userId = adminName,
                        userName = adminName,
                        entityType = "PRODUCT",
                        entityId = finalProduct.productId,
                        details = "Created product: ${finalProduct.name} (SKU: ${finalProduct.sku})"
                    )
                )
                if (finalProduct.stockQuantity > 0) {
                    db.inventoryTransactionDao().insert(
                        InventoryTransactionEntity(
                            transactionId = "tx_${UUID.randomUUID()}",
                            productId = finalProduct.productId,
                            productName = finalProduct.name,
                            sku = finalProduct.sku,
                            type = TransactionType.STOCK_ADDED,
                            quantity = finalProduct.stockQuantity,
                            previousStock = 0,
                            newStock = finalProduct.stockQuantity,
                            reason = "Initial inventory when adding product",
                            createdBy = adminName
                        )
                    )
                }
            } else {
                val oldProduct = db.productDao().getProductByIdDirect(finalProduct.productId)
                db.productDao().insert(finalProduct)
                db.auditLogDao().insert(
                    AuditLogEntity(
                        logId = "log_${UUID.randomUUID()}",
                        action = "PRODUCT_UPDATED",
                        userId = adminName,
                        userName = adminName,
                        entityType = "PRODUCT",
                        entityId = finalProduct.productId,
                        oldValue = "Stock: ${oldProduct?.stockQuantity}, Price: ₹${oldProduct?.sellingPrice}",
                        newValue = "Stock: ${finalProduct.stockQuantity}, Price: ₹${finalProduct.sellingPrice}",
                        details = "Updated details for ${finalProduct.name}"
                    )
                )
            }
        } catch (_: Exception) {}

        Result.success(Unit)
    }

    suspend fun bulkImportProducts(
        products: List<ProductEntity>,
        adminName: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (products.isEmpty()) return@withContext Result.success(0)

        // 1. Primary write to Firestore using batched writes
        android.util.Log.d("AnuToolsRepository", "BULK_IMPORT_START -> Importing ${products.size} products to Firestore")
        val firestoreResult = firestoreProductRepository.saveProductsBatch(products, adminName)
        if (firestoreResult.isFailure) {
            android.util.Log.e("AnuToolsRepository", "BULK_IMPORT_FAILURE -> Failed to write batch to Firestore", firestoreResult.exceptionOrNull())
            return@withContext firestoreResult
        }

        // 2. Cache in local Room database
        try {
            db.productDao().insertAll(products)
            android.util.Log.d("AnuToolsRepository", "BULK_IMPORT_ROOM -> Cached ${products.size} products in Room")

            // Write initial inventory transactions for items with stock > 0
            val transactions = products.filter { it.stockQuantity > 0 }.map { prod ->
                InventoryTransactionEntity(
                    transactionId = "tx_${UUID.randomUUID()}",
                    productId = prod.productId,
                    productName = prod.name,
                    sku = prod.sku,
                    type = TransactionType.STOCK_ADDED,
                    quantity = prod.stockQuantity,
                    previousStock = 0,
                    newStock = prod.stockQuantity,
                    reason = "Bulk CSV import",
                    createdBy = adminName
                )
            }
            if (transactions.isNotEmpty()) {
                db.inventoryTransactionDao().insertAll(transactions)
            }

            // Write audit log entry
            db.auditLogDao().insert(
                AuditLogEntity(
                    logId = "log_${UUID.randomUUID()}",
                    action = "BULK_PRODUCT_IMPORT",
                    userId = adminName,
                    userName = adminName,
                    entityType = "PRODUCT",
                    entityId = "batch_${System.currentTimeMillis()}",
                    details = "Bulk imported ${products.size} products via CSV"
                )
            )
        } catch (e: Exception) {
            android.util.Log.w("AnuToolsRepository", "Local Room cache / audit failed for bulk import: ${e.message}")
        }

        firestoreResult
    }

    suspend fun deleteProduct(productId: String, adminName: String): Result<Unit> = withContext(Dispatchers.IO) {
        deleteProducts(listOf(productId), adminName).map { }
    }

    suspend fun deleteProducts(productIds: List<String>, adminName: String): Result<Int> = withContext(Dispatchers.IO) {
        if (productIds.isEmpty()) return@withContext Result.success(0)

        // 1. Delete from Firestore
        android.util.Log.d("AnuToolsRepository", "DELETE_PRODUCTS -> Deleting ${productIds.size} products from Firestore")
        val firestoreResult = firestoreProductRepository.deleteProducts(productIds, adminName)
        if (firestoreResult.isFailure) {
            android.util.Log.e("AnuToolsRepository", "DELETE_PRODUCTS_FAILURE -> Failed to delete from Firestore", firestoreResult.exceptionOrNull())
            return@withContext firestoreResult
        }

        // 2. Delete from local Room cache
        try {
            db.productDao().deleteByIds(productIds)
            android.util.Log.d("AnuToolsRepository", "DELETE_PRODUCTS_ROOM -> Removed ${productIds.size} products from Room")
            db.auditLogDao().insert(
                AuditLogEntity(
                    logId = "log_${UUID.randomUUID()}",
                    action = "PRODUCTS_DELETED",
                    userId = adminName,
                    userName = adminName,
                    entityType = "PRODUCT",
                    entityId = productIds.firstOrNull() ?: "",
                    details = "Deleted ${productIds.size} product(s) from catalog"
                )
            )
        } catch (e: Exception) {
            android.util.Log.w("AnuToolsRepository", "Room delete / audit failed: ${e.message}")
        }

        firestoreResult
    }

    suspend fun adjustProductStock(productId: String, quantityChange: Int, reason: String, adminName: String): Result<Unit> = withContext(Dispatchers.IO) {
        // Primary operation in Firestore
        val firestoreResult = firestoreProductRepository.adjustProductStock(productId, quantityChange, reason, adminName)
        if (firestoreResult.isFailure) {
            return@withContext firestoreResult
        }

        // Only update local Room database after Firestore succeeds
        try {
            val product = db.productDao().getProductByIdDirect(productId) ?: return@withContext Result.success(Unit)
            val newQuantity = (product.stockQuantity + quantityChange).coerceAtLeast(0)
            val newStatus = ProductEntity.calculateStockStatus(newQuantity, product.minimumStockLevel)
            val now = System.currentTimeMillis()

            db.productDao().updateStock(productId, newQuantity, newStatus, now)

            val txType = if (quantityChange >= 0) TransactionType.STOCK_ADDED else TransactionType.STOCK_REMOVED
            db.inventoryTransactionDao().insert(
                InventoryTransactionEntity(
                    transactionId = "tx_${UUID.randomUUID()}",
                    productId = product.productId,
                    productName = product.name,
                    sku = product.sku,
                    type = txType,
                    quantity = quantityChange,
                    previousStock = product.stockQuantity,
                    newStock = newQuantity,
                    reason = reason.ifBlank { "Manual stock adjustment" },
                    createdBy = adminName
                )
            )

            db.auditLogDao().insert(
                AuditLogEntity(
                    logId = "log_${UUID.randomUUID()}",
                    action = "STOCK_CHANGED",
                    userId = adminName,
                    userName = adminName,
                    entityType = "PRODUCT",
                    entityId = productId,
                    oldValue = "${product.stockQuantity}",
                    newValue = "$newQuantity",
                    details = "Stock adjusted by $quantityChange ($reason)"
                )
            )

            if (newStatus == StockStatus.LOW_STOCK || newStatus == StockStatus.OUT_OF_STOCK) {
                val notifType = if (newStatus == StockStatus.OUT_OF_STOCK) NotificationType.OUT_OF_STOCK else NotificationType.LOW_STOCK
                db.notificationDao().insert(
                    NotificationItemEntity(
                        notificationId = "notif_${UUID.randomUUID()}",
                        type = notifType,
                        title = if (newStatus == StockStatus.OUT_OF_STOCK) "Product Out of Stock!" else "Low Stock Warning",
                        message = "${product.name} is now at $newQuantity ${product.unit}(s).",
                        productId = product.productId
                    )
                )
            }
        } catch (_: Exception) {}

        Result.success(Unit)
    }

    suspend fun toggleProductActive(productId: String, active: Boolean, adminName: String): Result<Unit> = withContext(Dispatchers.IO) {
        // Primary operation in Firestore
        val firestoreResult = firestoreProductRepository.toggleProductArchive(productId, active, adminName)
        if (firestoreResult.isFailure) {
            return@withContext firestoreResult
        }

        // Only update local Room after Firestore succeeds
        try {
            val now = System.currentTimeMillis()
            db.productDao().setProductActive(productId, active, now)
            db.auditLogDao().insert(
                AuditLogEntity(
                    logId = "log_${UUID.randomUUID()}",
                    action = if (active) "PRODUCT_ACTIVATED" else "PRODUCT_ARCHIVED",
                    userId = adminName,
                    userName = adminName,
                    entityType = "PRODUCT",
                    entityId = productId,
                    details = if (active) "Reactivated product in catalog" else "Archived product (hidden from retailer app)"
                )
            )
        } catch (_: Exception) {}

        Result.success(Unit)
    }

    suspend fun updateOrderStatus(
        orderId: String,
        newStatus: OrderStatus,
        adminName: String,
        note: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val order = db.orderDao().getOrderByIdDirect(orderId)
            ?: return@withContext Result.failure(Exception("Order not found"))

        val currentStatus = order.orderStatus
        if (currentStatus == newStatus) return@withContext Result.success(Unit)

        val now = System.currentTimeMillis()

        // 1. Sync directly to the SAME Firestore orders/{orderId} document FIRST.
        // If confirming, this atomically verifies stock and deducts it.
        // If stock is insufficient, it aborts cleanly without partial state.
        val firestoreResult = firestoreOrderRepository.updateOrderStatus(orderId, newStatus, adminName, note)
        if (firestoreResult.isFailure) {
            return@withContext firestoreResult
        }

        // 2. Handle inventory adjustments on status change locally in Room
        if (newStatus == OrderStatus.CONFIRMED && currentStatus == OrderStatus.PENDING) {
            // Deduct inventory
            val items = parseItemsJson(order.itemsJson)
            for (item in items) {
                val prod = db.productDao().getProductByIdDirect(item.productId)
                if (prod != null) {
                    val updatedQty = (prod.stockQuantity - item.quantity).coerceAtLeast(0)
                    val updatedStatus = ProductEntity.calculateStockStatus(updatedQty, prod.minimumStockLevel)
                    db.productDao().updateStock(prod.productId, updatedQty, updatedStatus, now)
                    db.inventoryTransactionDao().insert(
                        InventoryTransactionEntity(
                            transactionId = "tx_${UUID.randomUUID()}",
                            productId = prod.productId,
                            productName = prod.name,
                            sku = prod.sku,
                            type = TransactionType.ORDER_CONFIRMED,
                            quantity = -item.quantity,
                            previousStock = prod.stockQuantity,
                            newStock = updatedQty,
                            reason = "Stock deducted for confirmed order ${order.orderNumber}",
                            orderId = order.orderId,
                            createdBy = adminName
                        )
                    )
                }
            }
        } else if (newStatus == OrderStatus.CANCELLED && currentStatus != OrderStatus.PENDING && currentStatus != OrderStatus.CANCELLED) {
            // Restock inventory if order was confirmed before being cancelled
            val items = parseItemsJson(order.itemsJson)
            for (item in items) {
                val prod = db.productDao().getProductByIdDirect(item.productId)
                if (prod != null) {
                    val updatedQty = prod.stockQuantity + item.quantity
                    val updatedStatus = ProductEntity.calculateStockStatus(updatedQty, prod.minimumStockLevel)
                    db.productDao().updateStock(prod.productId, updatedQty, updatedStatus, now)
                    db.inventoryTransactionDao().insert(
                        InventoryTransactionEntity(
                            transactionId = "tx_${UUID.randomUUID()}",
                            productId = prod.productId,
                            productName = prod.name,
                            sku = prod.sku,
                            type = TransactionType.ORDER_CANCELLED,
                            quantity = item.quantity,
                            previousStock = prod.stockQuantity,
                            newStock = updatedQty,
                            reason = "Stock restored due to order cancellation for ${order.orderNumber}",
                            orderId = order.orderId,
                            createdBy = adminName
                        )
                    )
                }
            }
        }

        // Append to status history JSON
        val historyArray = try {
            JSONArray(order.statusHistoryJson)
        } catch (e: Exception) {
            JSONArray()
        }
        val historyEntry = JSONObject().apply {
            put("status", newStatus.name)
            put("changedAt", now)
            put("changedBy", adminName)
            if (note.isNotBlank()) put("notes", note)
        }
        historyArray.put(historyEntry)

        val updatedOrder = order.copy(
            orderStatus = newStatus,
            statusHistoryJson = historyArray.toString(),
            updatedAt = now,
            confirmedAt = if (newStatus == OrderStatus.CONFIRMED && order.confirmedAt == null) now else order.confirmedAt,
            dispatchedAt = if (newStatus == OrderStatus.OUT_FOR_DELIVERY && order.dispatchedAt == null) now else order.dispatchedAt,
            deliveredAt = if (newStatus == OrderStatus.DELIVERED && order.deliveredAt == null) now else order.deliveredAt,
            cancelledAt = if (newStatus == OrderStatus.CANCELLED && order.cancelledAt == null) now else order.cancelledAt
        )
        db.orderDao().update(updatedOrder)

        db.auditLogDao().insert(
            AuditLogEntity(
                logId = "log_${UUID.randomUUID()}",
                action = "ORDER_STATUS_CHANGED",
                userId = adminName,
                userName = adminName,
                entityType = "ORDER",
                entityId = order.orderNumber,
                oldValue = currentStatus.name,
                newValue = newStatus.name,
                details = "Status updated from ${currentStatus.displayName} to ${newStatus.displayName}. $note"
            )
        )

        return@withContext Result.success(Unit)
    }

    suspend fun updatePaymentStatus(
        orderId: String,
        newStatus: PaymentStatus,
        amountPaid: Double,
        reference: String,
        note: String,
        verifiedBy: String
    ) = withContext(Dispatchers.IO) {
        val order = db.orderDao().getOrderByIdDirect(orderId) ?: return@withContext
        val now = System.currentTimeMillis()
        val actualAmount = if (newStatus == PaymentStatus.PAID) order.total else amountPaid
        val updatedOrder = order.copy(
            paymentStatus = newStatus,
            amountPaid = actualAmount,
            paymentReference = reference,
            paymentNote = note,
            paymentVerifiedBy = verifiedBy,
            paymentVerifiedAt = now,
            updatedAt = now
        )
        db.orderDao().update(updatedOrder)

        db.auditLogDao().insert(
            AuditLogEntity(
                logId = "log_${UUID.randomUUID()}",
                action = "PAYMENT_STATUS_CHANGED",
                userId = verifiedBy,
                userName = verifiedBy,
                entityType = "ORDER",
                entityId = order.orderNumber,
                oldValue = order.paymentStatus.name,
                newValue = newStatus.name,
                details = "Payment marked as ${newStatus.displayName}. Ref: $reference, Amount: ₹$actualAmount"
            )
        )

        // Sync directly to the SAME Firestore orders/{orderId} document
        val firestoreResult = firestoreOrderRepository.updatePaymentStatus(
            orderId = orderId,
            newStatus = newStatus,
            amountPaid = actualAmount,
            reference = reference,
            note = note,
            verifiedBy = verifiedBy
        )
        if (firestoreResult.isFailure) {
            Log.w("AnuToolsRepository", "Firestore payment status sync warning: ${firestoreResult.exceptionOrNull()?.message}")
        }
    }

    suspend fun updateOrderNotes(orderId: String, internalNote: String, customerNote: String) = withContext(Dispatchers.IO) {
        val order = db.orderDao().getOrderByIdDirect(orderId) ?: return@withContext
        db.orderDao().update(
            order.copy(
                internalNote = internalNote,
                customerNote = customerNote,
                updatedAt = System.currentTimeMillis()
            )
        )

        // Sync directly to the SAME Firestore orders/{orderId} document
        val firestoreResult = firestoreOrderRepository.updateOrderNotes(orderId, internalNote, customerNote)
        if (firestoreResult.isFailure) {
            Log.w("AnuToolsRepository", "Firestore order notes sync warning: ${firestoreResult.exceptionOrNull()?.message}")
        }
    }

    suspend fun saveCategory(category: CategoryEntity) = withContext(Dispatchers.IO) {
        // Save to Room for offline access
        db.categoryDao().insert(category)
        // Sync to Firestore so retailer/customer app gets the updated catalog immediately
        val result = firestoreCatalogRepository.saveCategory(category)
        if (result.isFailure) {
            Log.w("AnuToolsRepository", "Category Firestore sync failed (saved locally): ${result.exceptionOrNull()?.message}")
        }
    }

    suspend fun deleteCategory(category: CategoryEntity) = withContext(Dispatchers.IO) {
        db.categoryDao().delete(category)
        val result = firestoreCatalogRepository.deleteCategory(category.categoryId)
        if (result.isFailure) {
            Log.w("AnuToolsRepository", "Category Firestore delete failed: ${result.exceptionOrNull()?.message}")
        }
    }

    suspend fun saveBrand(brand: BrandEntity) = withContext(Dispatchers.IO) {
        // Save to Room for offline access
        db.brandDao().insert(brand)
        // Sync to Firestore so retailer/customer app gets the updated brand list immediately
        val result = firestoreCatalogRepository.saveBrand(brand)
        if (result.isFailure) {
            Log.w("AnuToolsRepository", "Brand Firestore sync failed (saved locally): ${result.exceptionOrNull()?.message}")
        }
    }

    suspend fun deleteBrand(brand: BrandEntity) = withContext(Dispatchers.IO) {
        db.brandDao().delete(brand)
        val result = firestoreCatalogRepository.deleteBrand(brand.brandId)
        if (result.isFailure) {
            Log.w("AnuToolsRepository", "Brand Firestore delete failed: ${result.exceptionOrNull()?.message}")
        }
    }

    suspend fun saveSettings(settings: BusinessSettingsEntity, adminName: String) = withContext(Dispatchers.IO) {
        val updated = settings.copy(updatedAt = System.currentTimeMillis())
        db.businessSettingsDao().insertOrUpdate(updated)

        // Sync directly to Firestore collection "businessSettings" so Retailer app reads the exact same settings in real time
        val result = firestoreSettingsRepository.saveBusinessSettings(updated)
        if (result.isFailure) {
            Log.w("AnuToolsRepository", "Failed to sync settings to Firestore: ${result.exceptionOrNull()?.message}")
        }

        db.auditLogDao().insert(
            AuditLogEntity(
                logId = "log_${UUID.randomUUID()}",
                action = "SETTING_UPDATED",
                userId = adminName,
                userName = adminName,
                entityType = "SETTINGS",
                entityId = "default_settings",
                details = "Updated business profile, payment rules & delivery fee"
            )
        )
    }

    suspend fun updateRetailerStatus(retailerId: String, newStatus: RetailerStatus, adminName: String) = withContext(Dispatchers.IO) {
        val retailer = db.retailerDao().getRetailerByIdDirect(retailerId)
        val now = System.currentTimeMillis()
        if (retailer != null) {
            db.retailerDao().update(retailer.copy(status = newStatus, updatedAt = now))
        }
        val result = firestoreRetailerRepository.updateRetailerStatus(retailerId, newStatus)
        if (result.isFailure) {
            Log.w("AnuToolsRepository", "Failed to update retailer status in Firestore: ${result.exceptionOrNull()?.message}")
        }
        db.auditLogDao().insert(
            AuditLogEntity(
                logId = "log_${UUID.randomUUID()}",
                action = "RETAILER_STATUS_CHANGED",
                userId = adminName,
                userName = adminName,
                entityType = "RETAILER",
                entityId = retailerId,
                oldValue = retailer?.status?.name ?: "",
                newValue = newStatus.name,
                details = "Updated retailer status to ${newStatus.displayName}"
            )
        )
    }

    suspend fun markNotificationAsRead(notificationId: String) = withContext(Dispatchers.IO) {
        db.notificationDao().markAsRead(notificationId)
    }

    suspend fun markAllNotificationsAsRead() = withContext(Dispatchers.IO) {
        db.notificationDao().markAllAsRead()
    }

    // Helper method to place a sample new order (useful during presentation to show real-time notification and order incoming flow!)
    suspend fun createSimulatedRetailerOrder(retailerId: String, selectedProducts: List<Pair<ProductEntity, Int>>, paymentMethod: PaymentMethod): String = withContext(Dispatchers.IO) {
        val retailer = db.retailerDao().getRetailerByIdDirect(retailerId) ?: return@withContext ""
        val now = System.currentTimeMillis()
        val orderNum = "ANU-2026-000${(124..999).random()}"
        val orderId = "ord_${UUID.randomUUID().toString().take(8)}"

        val itemsArray = JSONArray()
        var subtotal = 0.0
        var totalGst = 0.0
        var totalQty = 0

        for ((product, qty) in selectedProducts) {
            val itemSubtotal = product.retailerPrice * qty
            val itemGst = itemSubtotal * (product.gstPercentage / 100.0)
            subtotal += itemSubtotal
            totalGst += itemGst
            totalQty += qty

            val obj = JSONObject().apply {
                put("productId", product.productId)
                put("sku", product.sku)
                put("name", product.name)
                put("imageUrl", product.thumbnailUrl)
                put("quantity", qty)
                put("unit", product.unit)
                put("unitPrice", product.retailerPrice)
                put("mrp", product.mrp)
                put("gstPercentage", product.gstPercentage)
                put("gstAmount", itemGst)
                put("subtotal", itemSubtotal)
            }
            itemsArray.put(obj)
        }

        val total = subtotal + totalGst
        val history = JSONArray().put(
            JSONObject().apply {
                put("status", "PENDING")
                put("changedAt", now)
                put("changedBy", "${retailer.shopName} (Retailer App)")
                put("notes", "Order placed through Retailer App")
            }
        )

        val newOrder = OrderEntity(
            orderId = orderId,
            orderNumber = orderNum,
            retailerId = retailer.retailerId,
            retailerName = retailer.name,
            shopName = retailer.shopName,
            phone = retailer.phone,
            address = retailer.address,
            city = retailer.city,
            pincode = retailer.pincode,
            itemsJson = itemsArray.toString(),
            itemsCount = totalQty,
            subtotal = subtotal,
            discount = 0.0,
            gst = totalGst,
            deliveryCharge = 0.0,
            total = total,
            paymentMethod = paymentMethod,
            paymentStatus = if (paymentMethod == PaymentMethod.QR_PAYMENT) PaymentStatus.PAYMENT_PENDING_VERIFICATION else PaymentStatus.UNPAID,
            orderStatus = OrderStatus.PENDING,
            statusHistoryJson = history.toString(),
            createdAt = now,
            updatedAt = now
        )
        db.orderDao().insert(newOrder)

        // Trigger notification
        db.notificationDao().insert(
            NotificationItemEntity(
                notificationId = "notif_${UUID.randomUUID()}",
                type = NotificationType.NEW_ORDER,
                title = "New Order Received",
                message = "Order $orderNum from ${retailer.shopName} (₹${String.format("%.0f", total)})",
                orderId = orderId,
                read = false,
                createdAt = now
            )
        )

        return@withContext orderNum
    }

    private fun parseItemsJson(jsonStr: String): List<OrderItemSnapshot> {
        val list = mutableListOf<OrderItemSnapshot>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val rawImg = obj.optString("imageUrl").ifBlank {
                    obj.optString("thumbnailUrl").ifBlank {
                        obj.optString("image").ifBlank {
                            obj.optString("thumbnail", "")
                        }
                    }
                }
                val img = if (rawImg.trim().startsWith("data:", ignoreCase = true)) "" else rawImg.trim()
                list.add(
                    OrderItemSnapshot(
                        productId = obj.optString("productId", ""),
                        sku = obj.optString("sku", ""),
                        name = obj.optString("name", ""),
                        imageUrl = img,
                        quantity = obj.optInt("quantity", 1),
                        unit = obj.optString("unit", "Piece"),
                        unitPrice = obj.optDouble("unitPrice", 0.0),
                        mrp = obj.optDouble("mrp", 0.0),
                        gstPercentage = obj.optDouble("gstPercentage", 18.0),
                        gstAmount = obj.optDouble("gstAmount", 0.0),
                        subtotal = obj.optDouble("subtotal", 0.0)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
