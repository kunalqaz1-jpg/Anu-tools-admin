package com.example.data.remote

import android.util.Log
import com.example.data.model.*
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

/**
 * Firestore repository for real-time order synchronization with the Retailer app.
 *
 * Connects directly to Firestore collection: "orders"
 * Project: "anu-tools-production"
 *
 * Provides real-time snapshot listeners for immediate order delivery to the admin dashboard,
 * and handles order workflow and payment verification updates directly on the shared document.
 */
class FirestoreOrderRepository {

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    private val ordersCollection get() = firestore.collection("orders")

    companion object {
        private const val TAG = "FirestoreOrderRepo"
    }

    /**
     * Real-time stream of all orders from Firestore.
     * Uses an addSnapshotListener so newly created retailer orders appear automatically
     * without any manual refresh.
     */
    val ordersFlow: Flow<List<OrderEntity>> = callbackFlow {
        val registration = ordersCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error fetching orders from Firestore", error)
                trySend(emptyList())
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val orderList = snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toOrderEntity()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse order document ${doc.id}: ${e.message}")
                        null
                    }
                }.sortedByDescending { it.createdAt }

                Log.d(TAG, "Successfully loaded ${orderList.size} real orders from Firestore")
                trySend(orderList)
            }
        }
        awaitClose { registration.remove() }
    }

    /**
     * Real-time stream of a single order by ID.
     */
    fun getOrderFlow(orderId: String): Flow<OrderEntity?> = callbackFlow {
        if (orderId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val registration = ordersCollection.document(orderId).addSnapshotListener { doc, error ->
            if (error != null) {
                Log.e(TAG, "Error fetching order $orderId from Firestore", error)
                trySend(null)
                return@addSnapshotListener
            }
            if (doc != null && doc.exists()) {
                trySend(doc.toOrderEntity())
            } else {
                trySend(null)
            }
        }
        awaitClose { registration.remove() }
    }

    /**
     * Update order workflow status in the SAME orders/{orderId} document.
     * Preserves existing fields and updates status codes compatible with the retailer app.
     */
    suspend fun updateOrderStatus(
        orderId: String,
        newStatus: OrderStatus,
        adminName: String,
        note: String = ""
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()

            if (newStatus == OrderStatus.CONFIRMED) {
                // Feature 5: Automatic Stock Deduction using atomic Firestore Transaction
                firestore.runTransaction { transaction ->
                    val orderDocRef = ordersCollection.document(orderId)
                    val orderSnapshot = transaction.get(orderDocRef)
                    if (!orderSnapshot.exists()) {
                        throw IllegalStateException("Order $orderId does not exist in Firestore")
                    }

                    val alreadyDeducted = orderSnapshot.getBoolean("stockDeducted") ?: false
                    val currentStatus = OrderStatus.fromString(
                        orderSnapshot.getString("orderStatus") ?: orderSnapshot.getString("status")
                    )

                    // Deduct stock only once, when transitioning to CONFIRMED and not previously deducted
                    if (!alreadyDeducted && (currentStatus == OrderStatus.PENDING || currentStatus == OrderStatus.CONFIRMED)) {
                        val items = parseOrderItemsFromSnapshot(orderSnapshot)
                        val validItems = items.filter { it.productId.isNotBlank() && it.quantity > 0 }

                        if (validItems.isNotEmpty()) {
                            // Step 1: In Firestore transactions, ALL reads MUST precede all writes
                            val productDataList = validItems.map { item ->
                                val productRef = firestore.collection("products").document(item.productId)
                                val prodSnap = transaction.get(productRef)
                                Triple(item, productRef, prodSnap)
                            }

                            // Step 2: Check stock before deduction. If ANY product has insufficient stock, abort and do NOT partially deduct!
                            for ((item, _, prodSnap) in productDataList) {
                                if (!prodSnap.exists()) {
                                    throw IllegalStateException("Product '${item.name}' (ID: ${item.productId}) not found in catalog")
                                }
                                val currentStock = prodSnap.getLong("stockQuantity")?.toInt()
                                    ?: prodSnap.getLong("stock")?.toInt()
                                    ?: prodSnap.getDouble("stockQuantity")?.toInt()
                                    ?: 0

                                if (currentStock < item.quantity) {
                                    throw IllegalStateException("Insufficient stock for '${item.name}' (SKU: ${item.sku}): available $currentStock, required ${item.quantity}")
                                }
                            }

                            // Step 3: All products verified with sufficient stock! Now perform deductions in the transaction
                            for ((item, productRef, prodSnap) in productDataList) {
                                val currentStock = prodSnap.getLong("stockQuantity")?.toInt()
                                    ?: prodSnap.getLong("stock")?.toInt()
                                    ?: prodSnap.getDouble("stockQuantity")?.toInt()
                                    ?: 0
                                val newStock = currentStock - item.quantity
                                val minStock = prodSnap.getLong("minimumStockLevel")?.toInt() ?: 5
                                val newStockStatus = when {
                                    newStock <= 0 -> "OUT_OF_STOCK"
                                    newStock <= minStock -> "LOW_STOCK"
                                    else -> "IN_STOCK"
                                }

                                transaction.update(
                                    productRef,
                                    mapOf(
                                        "stockQuantity" to newStock,
                                        "stock" to newStock,
                                        "stockStatus" to newStockStatus,
                                        "updatedAt" to now
                                    )
                                )
                            }
                        }
                    }

                    // Prepare order updates in transaction
                    val updates = hashMapOf<String, Any>(
                        "orderStatus" to newStatus.firestoreCode,
                        "status" to newStatus.firestoreCode,
                        "confirmedAt" to now,
                        "updatedAt" to now,
                        "stockDeducted" to true,
                        "stockDeductedAt" to now
                    )
                    if (note.isNotBlank()) {
                        updates["statusNote"] = note
                    }

                    val historyEntry = hashMapOf<String, Any>(
                        "status" to newStatus.firestoreCode,
                        "changedAt" to now,
                        "changedBy" to adminName,
                        "notes" to if (note.isNotBlank()) note else "Order confirmed and stock deducted"
                    )
                    updates["statusHistory"] = FieldValue.arrayUnion(historyEntry)

                    transaction.set(orderDocRef, updates, SetOptions.merge())
                }.await()

                Log.d(TAG, "Successfully confirmed order $orderId with atomic stock deduction")
                Result.success(Unit)
            } else {
                // Status updates for PACKING, READY_FOR_DISPATCH, OUT_FOR_DELIVERY, DELIVERED, CANCELLED
                // Stock is NEVER deducted again for PACKING or later statuses
                val updates = hashMapOf<String, Any>(
                    "orderStatus" to newStatus.firestoreCode,
                    "status" to newStatus.firestoreCode,
                    "updatedAt" to now
                )

                if (note.isNotBlank()) {
                    updates["statusNote"] = note
                }

                when (newStatus) {
                    OrderStatus.OUT_FOR_DELIVERY -> updates["dispatchedAt"] = now
                    OrderStatus.DELIVERED -> updates["deliveredAt"] = now
                    OrderStatus.CANCELLED -> updates["cancelledAt"] = now
                    else -> {}
                }

                val historyEntry = hashMapOf<String, Any>(
                    "status" to newStatus.firestoreCode,
                    "changedAt" to now,
                    "changedBy" to adminName,
                    "notes" to note
                )
                updates["statusHistory"] = FieldValue.arrayUnion(historyEntry)

                ordersCollection.document(orderId).set(updates, SetOptions.merge()).await()
                Log.d(TAG, "Updated order $orderId status to ${newStatus.firestoreCode}")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update order status for $orderId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Update payment verification status in the SAME orders/{orderId} document.
     */
    suspend fun updatePaymentStatus(
        orderId: String,
        newStatus: PaymentStatus,
        amountPaid: Double,
        reference: String,
        note: String,
        verifiedBy: String
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val updates = hashMapOf<String, Any>(
                "paymentStatus" to newStatus.firestoreCode,
                "amountPaid" to amountPaid,
                "paymentAmount" to amountPaid,
                "paymentReference" to reference,
                "upiId" to reference,
                "paymentNote" to note,
                "verificationNotes" to note,
                "paymentVerifiedBy" to verifiedBy,
                "paymentVerifiedAt" to now,
                "updatedAt" to now
            )

            ordersCollection.document(orderId).set(updates, SetOptions.merge()).await()
            Log.d(TAG, "Updated payment status for $orderId to ${newStatus.firestoreCode}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update payment status for $orderId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Update operational notes in the SAME orders/{orderId} document.
     */
    suspend fun updateOrderNotes(
        orderId: String,
        internalNote: String,
        customerNote: String
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val updates = hashMapOf<String, Any>(
                "internalNote" to internalNote,
                "operationNote" to internalNote,
                "customerNote" to customerNote,
                "updatedAt" to now
            )

            ordersCollection.document(orderId).set(updates, SetOptions.merge()).await()
            Log.d(TAG, "Updated operational notes for $orderId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update operational notes for $orderId: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ──────────────────────────────────────────────────────────
    // MAPPING HELPERS (Robust parsing of retailer app documents)
    // ──────────────────────────────────────────────────────────

    private fun DocumentSnapshot.toOrderEntity(): OrderEntity {
        val targetOrderId = id.ifBlank {
            getString("orderId") ?: getString("id") ?: "order_${System.currentTimeMillis()}"
        }

        val orderNumber = getString("orderNumber")
            ?: getString("order_number")
            ?: getString("orderNo")
            ?: getString("order_no")
            ?: targetOrderId

        val retailerObj = get("retailer") as? Map<*, *>
        val shippingMap = (get("shippingAddress") as? Map<*, *>)
            ?: (get("deliveryAddress") as? Map<*, *>)
            ?: (get("address") as? Map<*, *>)

        val retailerId = getString("retailerId")
            ?: getString("userId")
            ?: getString("retailer_id")
            ?: getString("customerId")
            ?: (retailerObj?.get("id") as? String)
            ?: (retailerObj?.get("retailerId") as? String)
            ?: ""

        val retailerName = getString("retailerName")
            ?: getString("retailer_name")
            ?: getString("customerName")
            ?: getString("customer_name")
            ?: getString("userName")
            ?: getString("name")
            ?: (retailerObj?.get("name") as? String)
            ?: (retailerObj?.get("retailerName") as? String)
            ?: (shippingMap?.get("name") as? String)
            ?: "Retailer"

        val shopName = getString("shopName")
            ?: getString("shop_name")
            ?: getString("storeName")
            ?: getString("businessName")
            ?: (retailerObj?.get("shopName") as? String)
            ?: retailerName

        val phone = getString("phone")
            ?: getString("phoneNumber")
            ?: getString("phone_number")
            ?: getString("contact")
            ?: getString("contactNumber")
            ?: getString("mobile")
            ?: (retailerObj?.get("phone") as? String)
            ?: (shippingMap?.get("phone") as? String)
            ?: ""

        val alternatePhone = getString("alternatePhone")
            ?: getString("alternate_phone")
            ?: getString("altPhone")
            ?: (retailerObj?.get("alternatePhone") as? String)
            ?: ""

        val address = getString("address")
            ?: getString("deliveryAddress")
            ?: getString("delivery_address")
            ?: getString("shippingAddress")
            ?: (shippingMap?.get("address") as? String)
            ?: (shippingMap?.get("street") as? String)
            ?: (shippingMap?.get("line1") as? String)
            ?: ""

        val city = getString("city")
            ?: (shippingMap?.get("city") as? String)
            ?: ""

        val pincode = getString("pincode")
            ?: getString("pin_code")
            ?: getString("postalCode")
            ?: getString("zipCode")
            ?: (shippingMap?.get("pincode") as? String)
            ?: (shippingMap?.get("pin_code") as? String)
            ?: (shippingMap?.get("postalCode") as? String)
            ?: ""

        // Parse Items
        val parsedItems = parseOrderItemsFromSnapshot(this)

        val itemsJsonStr = if (parsedItems.isNotEmpty()) {
            val jsonArr = JSONArray()
            for (it in parsedItems) {
                val obj = JSONObject().apply {
                    put("productId", it.productId)
                    put("sku", it.sku)
                    put("name", it.name)
                    put("imageUrl", it.imageUrl)
                    put("quantity", it.quantity)
                    put("unit", it.unit)
                    put("unitPrice", it.unitPrice)
                    put("mrp", it.mrp)
                    put("gstPercentage", it.gstPercentage)
                    put("gstAmount", it.gstAmount)
                    put("subtotal", it.subtotal)
                }
                jsonArr.put(obj)
            }
            jsonArr.toString()
        } else {
            getString("itemsJson") ?: "[]"
        }

        val itemsCount = (getLong("itemsCount") ?: getLong("totalItems"))?.toInt()
            ?: parsedItems.sumOf { it.quantity }.takeIf { it > 0 }
            ?: parsedItems.size.coerceAtLeast(1)

        var subtotal = extractDouble("subtotal", "subTotal", "sub_total", "itemsTotal")
        if (subtotal == 0.0 && parsedItems.isNotEmpty()) {
            subtotal = parsedItems.sumOf { it.subtotal }
        }

        val discount = extractDouble("discount", "discountAmount", "discount_amount")

        var gst = extractDouble("gst", "tax", "gstAmount", "taxAmount", "totalGst", "totalTax")
        if (gst == 0.0 && parsedItems.isNotEmpty()) {
            gst = parsedItems.sumOf { it.gstAmount }
        }

        val deliveryCharge = extractDouble("deliveryCharge", "deliveryFee", "delivery_fee", "shippingFee", "shippingCharge", "delivery_charge")

        var total = extractDouble("total", "grandTotal", "grand_total", "totalAmount", "total_amount", "amount")
        if (total == 0.0) {
            total = (subtotal - discount + gst + deliveryCharge).coerceAtLeast(0.0)
        }

        val paymentMethod = PaymentMethod.fromString(
            getString("paymentMethod") ?: getString("payment_method") ?: getString("paymentMode")
        )

        val paymentStatus = PaymentStatus.fromString(
            getString("paymentStatus") ?: getString("payment_status") ?: getString("paymentState")
        )

        val amountPaid = extractDouble("amountPaid", "amount_paid", "paymentAmount", "payment_amount", "paidAmount")

        val paymentReference = getString("paymentReference")
            ?: getString("payment_reference")
            ?: getString("upiId")
            ?: getString("upi_id")
            ?: getString("transactionId")
            ?: getString("utr")
            ?: ""

        val paymentNote = getString("paymentNote")
            ?: getString("payment_note")
            ?: getString("verificationNotes")
            ?: getString("verification_notes")
            ?: ""

        val paymentVerifiedBy = getString("paymentVerifiedBy")
            ?: getString("payment_verified_by")
            ?: ""

        val paymentVerifiedAt = extractOptionalTimestamp("paymentVerifiedAt", "payment_verified_at")

        val orderStatus = OrderStatus.fromString(
            getString("orderStatus") ?: getString("order_status") ?: getString("status") ?: getString("state")
        )

        val internalNote = getString("internalNote")
            ?: getString("internal_note")
            ?: getString("operationNote")
            ?: getString("operation_note")
            ?: getString("adminNotes")
            ?: ""

        val customerNote = getString("customerNote")
            ?: getString("customer_note")
            ?: getString("specialInstructions")
            ?: getString("instructions")
            ?: ""

        // Parse status history
        val rawHistory = get("statusHistory") ?: get("history")
        val statusHistoryJsonStr = if (rawHistory is List<*>) {
            val arr = JSONArray()
            for (item in rawHistory) {
                if (item is Map<*, *>) {
                    val hObj = JSONObject()
                    hObj.put("status", item["status"] ?: "")
                    val timeVal = item["changedAt"] ?: item["time"] ?: item["timestamp"]
                    val hTime = when (timeVal) {
                        is Number -> timeVal.toLong()
                        is com.google.firebase.Timestamp -> timeVal.toDate().time
                        else -> System.currentTimeMillis()
                    }
                    hObj.put("changedAt", hTime)
                    hObj.put("changedBy", item["changedBy"] ?: item["by"] ?: "")
                    hObj.put("notes", item["notes"] ?: item["note"] ?: "")
                    arr.put(hObj)
                }
            }
            arr.toString()
        } else {
            getString("statusHistoryJson") ?: "[]"
        }

        val createdAt = extractTimestamp("createdAt", "created_at", "timestamp", "orderDate", "order_date", "date")
        val updatedAt = extractTimestamp("updatedAt", "updated_at")
        val confirmedAt = extractOptionalTimestamp("confirmedAt", "confirmed_at")
        val dispatchedAt = extractOptionalTimestamp("dispatchedAt", "dispatched_at")
        val deliveredAt = extractOptionalTimestamp("deliveredAt", "delivered_at")
        val cancelledAt = extractOptionalTimestamp("cancelledAt", "cancelled_at")

        return OrderEntity(
            orderId = targetOrderId,
            orderNumber = orderNumber,
            retailerId = retailerId,
            retailerName = retailerName,
            shopName = shopName,
            phone = phone,
            alternatePhone = alternatePhone,
            address = address,
            city = city,
            pincode = pincode,
            itemsJson = itemsJsonStr,
            itemsCount = itemsCount,
            subtotal = subtotal,
            discount = discount,
            gst = gst,
            deliveryCharge = deliveryCharge,
            total = total,
            paymentMethod = paymentMethod,
            paymentStatus = paymentStatus,
            amountPaid = amountPaid,
            paymentReference = paymentReference,
            paymentNote = paymentNote,
            paymentVerifiedBy = paymentVerifiedBy,
            paymentVerifiedAt = paymentVerifiedAt,
            orderStatus = orderStatus,
            internalNote = internalNote,
            customerNote = customerNote,
            statusHistoryJson = statusHistoryJsonStr,
            createdAt = createdAt,
            updatedAt = updatedAt,
            confirmedAt = confirmedAt,
            dispatchedAt = dispatchedAt,
            deliveredAt = deliveredAt,
            cancelledAt = cancelledAt
        )
    }

    private fun DocumentSnapshot.extractTimestamp(vararg fields: String): Long {
        for (field in fields) {
            val raw = get(field)
            when (raw) {
                is Long -> return raw
                is Double -> return raw.toLong()
                is com.google.firebase.Timestamp -> return raw.toDate().time
                is java.util.Date -> return raw.time
                is String -> raw.toLongOrNull()?.let { return it }
            }
        }
        return System.currentTimeMillis()
    }

    private fun DocumentSnapshot.extractOptionalTimestamp(vararg fields: String): Long? {
        for (field in fields) {
            val raw = get(field) ?: continue
            when (raw) {
                is Long -> return raw
                is Double -> return raw.toLong()
                is com.google.firebase.Timestamp -> return raw.toDate().time
                is java.util.Date -> return raw.time
                is String -> raw.toLongOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun DocumentSnapshot.extractDouble(vararg fields: String): Double {
        for (field in fields) {
            val raw = get(field) ?: continue
            when (raw) {
                is Double -> return raw
                is Long -> return raw.toDouble()
                is Int -> return raw.toDouble()
                is Float -> return raw.toDouble()
                is String -> raw.toDoubleOrNull()?.let { return it }
            }
        }
        return 0.0
    }

    private fun parseOrderItemsFromSnapshot(doc: DocumentSnapshot): List<OrderItemSnapshot> {
        val rawItems = doc.get("items")
            ?: doc.get("orderItems")
            ?: doc.get("order_items")
            ?: doc.get("itemsList")
            ?: doc.get("products")

        val parsedItems = mutableListOf<OrderItemSnapshot>()
        if (rawItems is List<*>) {
            for (raw in rawItems) {
                if (raw is Map<*, *>) {
                    val pId = (raw["productId"] ?: raw["id"] ?: raw["product_id"] ?: "") as? String ?: ""
                    val pSku = (raw["sku"] ?: raw["productSku"] ?: "") as? String ?: ""
                    val pName = (raw["name"] ?: raw["productName"] ?: raw["title"] ?: "Item") as? String ?: "Item"
                    val pImgRaw = (raw["imageUrl"] ?: raw["thumbnailUrl"] ?: raw["image"] ?: raw["thumbnail"] ?: raw["productImage"] ?: raw["img"] ?: "") as? String ?: ""
                    val pImg = if (pImgRaw.trim().startsWith("data:", ignoreCase = true)) "" else pImgRaw.trim()
                    val pQty = (raw["quantity"] as? Number)?.toInt()
                        ?: (raw["qty"] as? Number)?.toInt()
                        ?: (raw["count"] as? Number)?.toInt()
                        ?: 1
                    val pUnit = (raw["unit"] as? String) ?: "Piece"
                    val pUnitPrice = (raw["unitPrice"] as? Number)?.toDouble()
                        ?: (raw["price"] as? Number)?.toDouble()
                        ?: (raw["rate"] as? Number)?.toDouble()
                        ?: 0.0
                    val pMrp = (raw["mrp"] as? Number)?.toDouble() ?: pUnitPrice
                    val pGstPct = (raw["gstPercentage"] as? Number)?.toDouble()
                        ?: (raw["gst"] as? Number)?.toDouble()
                        ?: (raw["taxPercent"] as? Number)?.toDouble()
                        ?: 18.0
                    val pGstAmt = (raw["gstAmount"] as? Number)?.toDouble()
                        ?: ((pUnitPrice * pQty) * (pGstPct / 100.0))
                    val pSubtotal = (raw["subtotal"] as? Number)?.toDouble()
                        ?: (raw["total"] as? Number)?.toDouble()
                        ?: (pUnitPrice * pQty)

                    parsedItems.add(
                        OrderItemSnapshot(
                            productId = pId,
                            sku = pSku,
                            name = pName,
                            imageUrl = pImg,
                            quantity = pQty,
                            unit = pUnit,
                            unitPrice = pUnitPrice,
                            mrp = pMrp,
                            gstPercentage = pGstPct,
                            gstAmount = pGstAmt,
                            subtotal = pSubtotal
                        )
                    )
                }
            }
        }
        return parsedItems
    }
}
