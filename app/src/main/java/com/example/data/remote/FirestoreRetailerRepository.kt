package com.example.data.remote

import android.util.Log
import com.example.data.model.RetailerEntity
import com.example.data.model.RetailerStatus
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore repository for real-time Retailer synchronization.
 *
 * Connects directly to Firestore collection: "retailers"
 * Project: "anu-tools-production"
 *
 * Provides real-time snapshot listeners so verified retailer accounts created or updated
 * in the Retailer app appear automatically in the Admin dashboard.
 */
class FirestoreRetailerRepository {

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    private val retailersCollection get() = firestore.collection("retailers")

    companion object {
        private const val TAG = "FirestoreRetailerRepo"
    }

    /**
     * Real-time stream of all verified retailers from Firestore collection: retailers
     */
    val retailersFlow: Flow<List<RetailerEntity>> = callbackFlow {
        val registration = retailersCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error fetching retailers from Firestore", error)
                trySend(emptyList())
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val retailerList = snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toRetailerEntity()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse retailer document ${doc.id}: ${e.message}")
                        null
                    }
                }.sortedByDescending { it.totalBusinessValue }

                Log.d(TAG, "Successfully loaded ${retailerList.size} real retailers from Firestore")
                trySend(retailerList)
            }
        }
        awaitClose { registration.remove() }
    }

    /**
     * Real-time stream of a single retailer by ID.
     */
    fun getRetailerFlow(retailerId: String): Flow<RetailerEntity?> = callbackFlow {
        if (retailerId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val registration = retailersCollection.document(retailerId).addSnapshotListener { doc, error ->
            if (error != null) {
                Log.e(TAG, "Error fetching retailer $retailerId from Firestore", error)
                trySend(null)
                return@addSnapshotListener
            }
            if (doc != null && doc.exists()) {
                trySend(doc.toRetailerEntity())
            } else {
                trySend(null)
            }
        }
        awaitClose { registration.remove() }
    }

    /**
     * Update retailer active status in Firestore.
     */
    suspend fun updateRetailerStatus(retailerId: String, newStatus: RetailerStatus): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val updates = hashMapOf<String, Any>(
                "status" to newStatus.name,
                "active" to (newStatus == RetailerStatus.ACTIVE),
                "updatedAt" to now
            )
            retailersCollection.document(retailerId).set(updates, SetOptions.merge()).await()
            Log.d(TAG, "Updated retailer $retailerId status to ${newStatus.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update retailer status for $retailerId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Direct one-shot fetch of all retailers.
     */
    suspend fun getAllRetailersDirect(): List<RetailerEntity> {
        return try {
            val snapshot = retailersCollection.get().await()
            snapshot.documents.mapNotNull { it.toRetailerEntity() }
        } catch (e: Exception) {
            Log.e(TAG, "Direct fetch failed for retailers", e)
            emptyList()
        }
    }

    /**
     * Helper extension to map a Firestore DocumentSnapshot to RetailerEntity.
     * Sanitizes profilePhotoUrl to strictly exclude Base64 data and only accept valid URLs.
     */
    private fun DocumentSnapshot.toRetailerEntity(): RetailerEntity? {
        val retailerId = id

        // Shop Name
        val shopName = getString("shopName")
            ?: getString("shop_name")
            ?: getString("storeName")
            ?: getString("store_name")
            ?: getString("businessName")
            ?: getString("business_name")
            ?: "Retailer Shop"

        // Owner Name
        val ownerName = getString("name")
            ?: getString("ownerName")
            ?: getString("owner_name")
            ?: getString("contactPerson")
            ?: getString("contact_person")
            ?: shopName

        // Phone
        val phone = getString("phone")
            ?: getString("phoneNumber")
            ?: getString("phone_number")
            ?: getString("mobile")
            ?: ""

        // Email
        val email = getString("email")
            ?: getString("emailAddress")
            ?: ""

        // Address
        val address = getString("address")
            ?: getString("fullAddress")
            ?: getString("full_address")
            ?: getString("shopAddress")
            ?: ""

        // City, State, Pincode
        val city = getString("city") ?: ""
        val state = getString("state") ?: ""
        val pincode = getString("pincode")
            ?: getString("pinCode")
            ?: getString("pin_code")
            ?: getString("postalCode")
            ?: ""

        // Profile Photo URL - strictly Cloudinary or HTTPS image URL, NEVER Base64
        val rawPhotoUrl = getString("profilePhotoUrl")
            ?: getString("photoUrl")
            ?: getString("photo_url")
            ?: getString("avatarUrl")
            ?: getString("avatar_url")
            ?: getString("imageUrl")
            ?: getString("image_url")
            ?: ""

        val profilePhotoUrl = if (
            rawPhotoUrl.isNotBlank() &&
            !rawPhotoUrl.startsWith("data:", ignoreCase = true) &&
            (rawPhotoUrl.startsWith("http://", ignoreCase = true) || rawPhotoUrl.startsWith("https://", ignoreCase = true))
        ) {
            rawPhotoUrl.trim()
        } else {
            ""
        }

        // Active Status
        val rawStatus = getString("status")
        val boolActive = getBoolean("active") ?: getBoolean("isActive")
        val status = when {
            rawStatus != null -> RetailerStatus.fromString(rawStatus)
            boolActive != null -> if (boolActive) RetailerStatus.ACTIVE else RetailerStatus.INACTIVE
            else -> RetailerStatus.ACTIVE
        }

        // Order metrics
        val totalOrders = (getLong("totalOrders") ?: getLong("ordersCount") ?: getLong("orderCount"))?.toInt() ?: 0
        val totalBusinessValue = getDouble("totalBusinessValue")
            ?: getDouble("lifetimeBusinessValue")
            ?: (getLong("totalBusinessValue")?.toDouble())
            ?: (getLong("lifetimeBusinessValue")?.toDouble())
            ?: 0.0

        // Timestamps
        val createdAt = extractTimestamp("createdAt", "created_at")
        val updatedAt = extractTimestamp("updatedAt", "updated_at")

        return RetailerEntity(
            retailerId = retailerId,
            name = ownerName,
            shopName = shopName,
            phone = phone,
            email = email,
            address = address,
            city = city,
            state = state,
            pincode = pincode,
            profilePhotoUrl = profilePhotoUrl,
            totalOrders = totalOrders,
            totalBusinessValue = totalBusinessValue,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun DocumentSnapshot.extractTimestamp(vararg keys: String): Long {
        for (k in keys) {
            val v = get(k) ?: continue
            when (v) {
                is Number -> return v.toLong()
                is com.google.firebase.Timestamp -> return v.toDate().time
                is String -> v.toLongOrNull()?.let { return it }
            }
        }
        return System.currentTimeMillis()
    }
}
