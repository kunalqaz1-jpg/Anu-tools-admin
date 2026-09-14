package com.example.data.remote

import android.util.Log
import com.example.data.model.InventoryTransactionEntity
import com.example.data.model.ProductEntity
import com.example.data.model.StockStatus
import com.example.data.model.TransactionType
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class FirestoreProductRepository {

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    private val productsCollection get() = firestore.collection("products")
    private val auditLogsCollection get() = firestore.collection("auditLogs")
    private val inventoryTxCollection get() = firestore.collection("inventoryTransactions")
    private val notificationsCollection get() = firestore.collection("notifications")

    companion object {
        private const val TAG = "FirestoreProductRepo"
    }

    /**
     * Upload an image to Cloudinary at products/{productId}/{filename}.
     * Returns the HTTPS secure_url.
     */
    suspend fun uploadProductImage(productId: String, imageBytes: ByteArray, filename: String): Result<String> {
        return com.example.util.CloudinaryUploader.uploadImage(
            imageBytes = imageBytes,
            filename = filename,
            folder = "products/$productId"
        )
    }

    /**
     * Upload an image Uri to Cloudinary by reading its bytes.
     * Returns the HTTPS secure_url.
     */
    suspend fun uploadProductImage(productId: String, fileUri: android.net.Uri, filename: String): Result<String> {
        return try {
            val file = java.io.File(fileUri.path ?: "")
            val bytes = if (file.exists()) file.readBytes() else ByteArray(0)
            uploadProductImage(productId, bytes, filename)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete an image: client-side unsigned Cloudinary upload does not use API secret for deletions.
     */
    suspend fun deleteProductImage(storageUrlOrPath: String): Result<Unit> {
        Log.d(TAG, "Image deletion request for $storageUrlOrPath (unsigned client flow)")
        return Result.success(Unit)
    }

    /**
     * Realtime stream of all products from Firestore collection 'products'.
     */
    val productsFlow: Flow<List<ProductEntity>> = callbackFlow {
        val registration = productsCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error fetching products from Firestore", error)
                trySend(emptyList())
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val productList = snapshot.documents.mapNotNull { doc ->
                    doc.toProductEntity()
                }
                Log.d(TAG, "Successfully loaded ${productList.size} products from Firestore")
                trySend(productList)
            }
        }
        awaitClose { registration.remove() }
    }

    /**
     * Realtime stream of a single product by ID.
     */
    fun getProductFlow(productId: String): Flow<ProductEntity?> = callbackFlow {
        if (productId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val registration = productsCollection.document(productId).addSnapshotListener { doc, error ->
            if (error != null) {
                Log.e(TAG, "Error fetching product $productId from Firestore", error)
                trySend(null)
                return@addSnapshotListener
            }
            if (doc != null && doc.exists()) {
                trySend(doc.toProductEntity())
            } else {
                trySend(null)
            }
        }
        awaitClose { registration.remove() }
    }

    /**
     * Save (Create or Update) a product document in Firestore.
     */
    suspend fun saveProduct(product: ProductEntity, isNew: Boolean, adminName: String): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val calculatedStatus = ProductEntity.calculateStockStatus(product.stockQuantity, product.minimumStockLevel)
            val finalProduct = product.copy(
                stockStatus = calculatedStatus,
                updatedAt = now,
                updatedBy = adminName,
                createdBy = if (isNew) adminName else product.createdBy
            )

            val docRef = productsCollection.document(finalProduct.productId)
            val productMap = finalProduct.toFirestoreMap()
            docRef.set(productMap, SetOptions.merge()).await()

            // Write Audit Log to Firestore (best-effort; do not fail product save if audit log permissions fail)
            try {
                val logId = "log_${UUID.randomUUID()}"
                val auditData = mapOf(
                    "logId" to logId,
                    "action" to if (isNew) "PRODUCT_CREATED" else "PRODUCT_UPDATED",
                    "userId" to adminName,
                    "userName" to adminName,
                    "entityType" to "PRODUCT",
                    "entityId" to finalProduct.productId,
                    "details" to if (isNew) "Created product: ${finalProduct.name} (SKU: ${finalProduct.sku})" else "Updated product: ${finalProduct.name}",
                    "timestamp" to now
                )
                auditLogsCollection.document(logId).set(auditData).await()
            } catch (auditErr: Exception) {
                Log.w(TAG, "Audit log write skipped or failed: ${auditErr.message}")
            }

            // Initial stock transaction log if new product has stock (best-effort)
            if (isNew && finalProduct.stockQuantity > 0) {
                try {
                    val txId = "tx_${UUID.randomUUID()}"
                    val txData = mapOf(
                        "transactionId" to txId,
                        "productId" to finalProduct.productId,
                        "productName" to finalProduct.name,
                        "sku" to finalProduct.sku,
                        "type" to TransactionType.STOCK_ADDED.name,
                        "quantity" to finalProduct.stockQuantity,
                        "previousStock" to 0,
                        "newStock" to finalProduct.stockQuantity,
                        "reason" to "Initial inventory when adding product",
                        "createdBy" to adminName,
                        "createdAt" to now
                    )
                    inventoryTxCollection.document(txId).set(txData).await()
                } catch (txErr: Exception) {
                    Log.w(TAG, "Inventory transaction log write skipped or failed: ${txErr.message}")
                }
            }

            Log.d(TAG, "Product ${finalProduct.productId} saved successfully to Firestore")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save product ${product.productId} in Firestore", e)
            Result.failure(e)
        }
    }

    /**
     * Batch save multiple products into Firestore (used for bulk CSV import).
     * Firestore limits batches to 500 operations. We chunk into 400.
     */
    suspend fun saveProductsBatch(
        products: List<ProductEntity>,
        adminName: String
    ): Result<Int> {
        return try {
            val now = System.currentTimeMillis()
            var savedCount = 0
            val chunks = products.chunked(400)
            for (chunk in chunks) {
                val batch = firestore.batch()
                for (prod in chunk) {
                    val calculatedStatus = ProductEntity.calculateStockStatus(prod.stockQuantity, prod.minimumStockLevel)
                    val finalProduct = prod.copy(
                        stockStatus = calculatedStatus,
                        updatedAt = now,
                        updatedBy = adminName,
                        createdBy = if (prod.createdBy.isBlank() || prod.createdBy == "system") adminName else prod.createdBy
                    )
                    val docRef = productsCollection.document(finalProduct.productId)
                    batch.set(docRef, finalProduct.toFirestoreMap(), SetOptions.merge())
                    savedCount++
                }
                batch.commit().await()
            }
            Log.d(TAG, "Successfully batch saved $savedCount products to Firestore")
            Result.success(savedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to batch save products to Firestore", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a single product from Firestore.
     */
    suspend fun deleteProduct(productId: String, adminName: String): Result<Unit> {
        return deleteProducts(listOf(productId), adminName).map { }
    }

    /**
     * Delete multiple products from Firestore in chunks of up to 400.
     */
    suspend fun deleteProducts(productIds: List<String>, adminName: String): Result<Int> {
        return try {
            if (productIds.isEmpty()) return Result.success(0)
            val now = System.currentTimeMillis()
            var deletedCount = 0
            val chunks = productIds.chunked(400)
            for (chunk in chunks) {
                val batch = firestore.batch()
                for (id in chunk) {
                    val docRef = productsCollection.document(id)
                    batch.delete(docRef)
                    deletedCount++
                }
                batch.commit().await()
            }
            try {
                val logId = "log_${UUID.randomUUID()}"
                val auditData = mapOf(
                    "logId" to logId,
                    "action" to "PRODUCTS_DELETED",
                    "userId" to adminName,
                    "userName" to adminName,
                    "entityType" to "PRODUCT",
                    "entityId" to productIds.take(5).joinToString(","),
                    "details" to "Deleted $deletedCount product(s) from catalog",
                    "timestamp" to now
                )
                auditLogsCollection.document(logId).set(auditData).await()
            } catch (auditErr: Exception) {
                Log.w(TAG, "Audit log write skipped or failed: ${auditErr.message}")
            }
            Log.d(TAG, "Successfully deleted $deletedCount products from Firestore")
            Result.success(deletedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete products from Firestore", e)
            Result.failure(e)
        }
    }

    /**
     * Toggle product active/archived state in Firestore.
     */
    suspend fun toggleProductArchive(productId: String, active: Boolean, adminName: String): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            productsCollection.document(productId).update(
                mapOf(
                    "active" to active,
                    "updatedAt" to now,
                    "updatedBy" to adminName
                )
            ).await()

            try {
                val logId = "log_${UUID.randomUUID()}"
                val auditData = mapOf(
                    "logId" to logId,
                    "action" to if (active) "PRODUCT_ACTIVATED" else "PRODUCT_ARCHIVED",
                    "userId" to adminName,
                    "userName" to adminName,
                    "entityType" to "PRODUCT",
                    "entityId" to productId,
                    "details" to if (active) "Reactivated product in catalog" else "Archived product (hidden from retailer app)",
                    "timestamp" to now
                )
                auditLogsCollection.document(logId).set(auditData).await()
            } catch (auditErr: Exception) {
                Log.w(TAG, "Audit log write skipped or failed: ${auditErr.message}")
            }

            Log.d(TAG, "Product $productId archive status set to $active in Firestore")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle archive status for $productId", e)
            Result.failure(e)
        }
    }

    /**
     * Adjust product stock safely in Firestore.
     * Note: In future, secure Cloud Functions or atomic Firestore transactions should handle high-concurrency stock edits.
     */
    suspend fun adjustProductStock(productId: String, quantityChange: Int, reason: String, adminName: String): Result<Unit> {
        return try {
            val docRef = productsCollection.document(productId)
            val snap = docRef.get().await()
            if (!snap.exists()) return Result.failure(Exception("Product not found"))

            val currentStock = (snap.getLong("stockQuantity") ?: 0L).toInt()
            val minStock = (snap.getLong("minStock") ?: snap.getLong("minimumStockLevel") ?: 5L).toInt()
            val newQuantity = (currentStock + quantityChange).coerceAtLeast(0)
            val newStatus = ProductEntity.calculateStockStatus(newQuantity, minStock)
            val now = System.currentTimeMillis()

            docRef.update(
                mapOf(
                    "stockQuantity" to newQuantity,
                    "stockStatus" to newStatus.name,
                    "updatedAt" to now,
                    "updatedBy" to adminName
                )
            ).await()

            try {
                val txId = "tx_${UUID.randomUUID()}"
                val txType = if (quantityChange >= 0) TransactionType.STOCK_ADDED else TransactionType.STOCK_REMOVED
                val txData = mapOf(
                    "transactionId" to txId,
                    "productId" to productId,
                    "productName" to (snap.getString("name") ?: ""),
                    "sku" to (snap.getString("sku") ?: ""),
                    "type" to txType.name,
                    "quantity" to quantityChange,
                    "previousStock" to currentStock,
                    "newStock" to newQuantity,
                    "reason" to reason.ifBlank { "Manual stock adjustment" },
                    "createdBy" to adminName,
                    "createdAt" to now
                )
                inventoryTxCollection.document(txId).set(txData).await()
            } catch (txErr: Exception) {
                Log.w(TAG, "Inventory transaction write skipped or failed: ${txErr.message}")
            }

            if (newStatus == StockStatus.LOW_STOCK || newStatus == StockStatus.OUT_OF_STOCK) {
                try {
                    val notifId = "notif_${UUID.randomUUID()}"
                    val notifData = mapOf(
                        "notificationId" to notifId,
                        "type" to if (newStatus == StockStatus.OUT_OF_STOCK) "OUT_OF_STOCK" else "LOW_STOCK",
                        "title" to if (newStatus == StockStatus.OUT_OF_STOCK) "Product Out of Stock!" else "Low Stock Warning",
                        "message" to "${snap.getString("name")} is now at $newQuantity units.",
                        "productId" to productId,
                        "read" to false,
                        "createdAt" to now
                    )
                    notificationsCollection.document(notifId).set(notifData).await()
                } catch (notifErr: Exception) {
                    Log.w(TAG, "Notification write skipped or failed: ${notifErr.message}")
                }
            }

            Log.d(TAG, "Stock for $productId updated by $quantityChange in Firestore")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to adjust stock for $productId in Firestore", e)
            Result.failure(e)
        }
    }

    /**
     * Seeds initial sample product data to Firestore if the collection is empty.
     */
    suspend fun seedInitialProductsIfEmpty(sampleProducts: List<ProductEntity>) {
        try {
            val snapshot = productsCollection.limit(1).get().await()
            if (snapshot.isEmpty) {
                Log.d(TAG, "Firestore 'products' collection is empty. Seeding initial ${sampleProducts.size} products...")
                for (product in sampleProducts) {
                    productsCollection.document(product.productId).set(product.toFirestoreMap()).await()
                }
                Log.d(TAG, "Initial products seeded successfully to Firestore.")
            } else {
                Log.d(TAG, "Firestore 'products' collection already populated.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking/seeding Firestore products collection", e)
        }
    }

    /**
     * Mapper from DocumentSnapshot to ProductEntity
     */
    private fun DocumentSnapshot.toProductEntity(): ProductEntity? {
        if (!exists()) return null
        val docId = id.ifBlank { getString("id") ?: "" }
        val name = getString("name") ?: ""
        val slug = getString("slug") ?: ""
        val sku = getString("sku") ?: ""
        val brandId = getString("brandId") ?: ""
        val brandName = getString("brandName") ?: ""
        val categoryId = getString("categoryId") ?: ""
        val categoryName = getString("categoryName") ?: ""
        val subcategoryId = getString("subcategoryId") ?: ""
        val subcategoryName = getString("subcategoryName") ?: ""
        val shortDesc = getString("shortDescription") ?: getString("shortDesc") ?: ""
        val desc = getString("description") ?: getString("desc") ?: ""
        val specMap = get("specifications") as? Map<*, *>
        val specJson = when {
            getString("specificationsJson")?.isNotBlank() == true -> getString("specificationsJson")!!
            specMap != null -> JSONObject(specMap).toString()
            else -> "{}"
        }
        val mrp = extractDouble("mrp")
        val price = extractDouble("price", "sellingPrice")
        val retailerPrice = extractDouble("retailerPrice").takeIf { it > 0 } ?: price
        val discount = extractDouble("discount")
        val gst = extractDouble("gst", "gstPercentage").takeIf { it > 0 } ?: 18.0
        val stockQty = (getLong("stockQuantity") ?: getLong("stock") ?: 0L).toInt()
        val minStock = (getLong("minStock") ?: getLong("minimumStockLevel") ?: 5L).toInt()
        val unit = getString("unit") ?: "Piece"
        val active = getBoolean("active") ?: true
        val featured = getBoolean("featured") ?: false
        
        val imagesList = get("images") as? List<*>
        val imagesJson = if (imagesList != null) JSONArray(imagesList).toString() else (getString("imagesJson") ?: "[]")
        val thumbnail = getString("thumbnail") ?: getString("thumbnailUrl") ?: ""
        
        val createdAt = extractTimestamp("createdAt")
        val updatedAt = extractTimestamp("updatedAt")
        val createdBy = getString("createdBy") ?: "system"
        val updatedBy = getString("updatedBy") ?: "system"
        val calcStatus = ProductEntity.calculateStockStatus(stockQty, minStock)

        return ProductEntity(
            productId = docId,
            name = name,
            slug = slug,
            sku = sku,
            brandId = brandId,
            brandName = brandName,
            categoryId = categoryId,
            categoryName = categoryName,
            subcategoryId = subcategoryId,
            subcategoryName = subcategoryName,
            shortDescription = shortDesc,
            description = desc,
            specificationsJson = specJson,
            mrp = mrp,
            sellingPrice = price,
            retailerPrice = retailerPrice,
            discount = discount,
            gstPercentage = gst,
            stockQuantity = stockQty,
            minimumStockLevel = minStock,
            unit = unit,
            stockStatus = calcStatus,
            imagesJson = imagesJson,
            thumbnailUrl = thumbnail,
            active = active,
            featured = featured,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdBy = createdBy,
            updatedBy = updatedBy
        )
    }

    private fun DocumentSnapshot.extractTimestamp(field: String): Long {
        val raw = get(field)
        return when (raw) {
            is Long -> raw
            is Double -> raw.toLong()
            is com.google.firebase.Timestamp -> raw.toDate().time
            is java.util.Date -> raw.time
            else -> System.currentTimeMillis()
        }
    }

    private fun DocumentSnapshot.extractDouble(vararg fields: String): Double {
        for (field in fields) {
            val raw = get(field)
            when (raw) {
                is Double -> return raw
                is Long -> return raw.toDouble()
                is String -> raw.toDoubleOrNull()?.let { return it }
            }
        }
        return 0.0
    }

    /**
     * Mapper from ProductEntity to Firestore Map matching required document structure.
     */
    internal fun ProductEntity.toFirestoreMap(): Map<String, Any> {
        val imagesList = try {
            val jsonArray = JSONArray(imagesJson)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val s = jsonArray.optString(i)
                if (!s.isNullOrBlank()) list.add(s)
            }
            if (list.isEmpty() && thumbnailUrl.isNotBlank()) {
                list.add(thumbnailUrl)
            }
            list
        } catch (e: Exception) {
            if (thumbnailUrl.isNotBlank()) listOf(thumbnailUrl) else emptyList<String>()
        }

        val specsMap = mutableMapOf<String, Any>()
        try {
            val json = JSONObject(specificationsJson)
            json.keys().forEach { key ->
                val v = json.opt(key)
                if (v != null) specsMap[key] = v
            }
        } catch (_: Exception) {}

        val map = mutableMapOf<String, Any>(
            "id" to productId,
            "productId" to productId,
            "name" to name,
            "slug" to slug,
            "sku" to sku,
            "description" to description,
            "shortDescription" to shortDescription,
            "brandId" to brandId,
            "brandName" to brandName,
            "categoryId" to categoryId,
            "categoryName" to categoryName,
            "subcategoryId" to subcategoryId,
            "subcategoryName" to subcategoryName,
            "price" to sellingPrice,
            "sellingPrice" to sellingPrice,
            "mrp" to mrp,
            "retailerPrice" to retailerPrice,
            "discount" to discount,
            "gst" to gstPercentage,
            "gstPercentage" to gstPercentage,
            "stockQuantity" to stockQuantity,
            "minStock" to minimumStockLevel,
            "minimumStockLevel" to minimumStockLevel,
            "unit" to unit,
            "active" to active,
            "featured" to featured,
            "images" to imagesList,
            "imagesJson" to imagesJson,
            "thumbnail" to thumbnailUrl,
            "thumbnailUrl" to thumbnailUrl,
            "specificationsJson" to specificationsJson,
            "specifications" to specsMap,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "createdBy" to createdBy,
            "updatedBy" to updatedBy
        )

        // Map technical specs to top-level fields for convenience
        specsMap["Voltage"]?.let { map["voltage"] = it }
        specsMap["Cordless/Corded"]?.let { map["cordlessOrCorded"] = it }
        specsMap["Motor Type"]?.let { map["motorType"] = it }
        if (thumbnailUrl.isNotBlank()) {
            map["imageUrl"] = thumbnailUrl
        }

        return map
    }
}
