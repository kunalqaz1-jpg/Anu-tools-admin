package com.example.data.remote

import android.util.Log
import com.example.data.model.BusinessSettingsEntity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore repository for real-time Business Settings synchronization.
 *
 * Connects directly to Firestore collection: "businessSettings"
 * Project: "anu-tools-production"
 *
 * Shares settings with Retailer app so changes to Business Profile, COD/UPI,
 * Min/Max order amount and Delivery Fee are reflected in real time across apps.
 */
class FirestoreSettingsRepository {

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    private val settingsCollection get() = firestore.collection("businessSettings")

    companion object {
        private const val TAG = "FirestoreSettingsRepo"
        const val PRIMARY_DOC_ID = "default"
    }

    /**
     * Real-time stream of Business Settings from Firestore.
     * Listens to the businessSettings collection to support any docId ("default", "default_settings", etc.).
     */
    val settingsFlow: Flow<BusinessSettingsEntity?> = callbackFlow {
        val registration = settingsCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error fetching business settings from Firestore", error)
                trySend(null)
                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
                // Find primary document or fallback to first document
                val doc = snapshot.documents.find { it.id == PRIMARY_DOC_ID || it.id == "default_settings" }
                    ?: snapshot.documents.firstOrNull()

                val entity = doc?.toBusinessSettingsEntity()
                Log.d(TAG, "Successfully loaded real business settings from doc: ${doc?.id}")
                trySend(entity)
            } else {
                trySend(null)
            }
        }
        awaitClose { registration.remove() }
    }

    /**
     * Save business settings to Firestore.
     * Writes to "businessSettings/default" and creates/updates "businessSettings/default_settings" for backward compatibility.
     */
    suspend fun saveBusinessSettings(settings: BusinessSettingsEntity): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val data = hashMapOf<String, Any>(
                // Business Profile
                "businessName" to settings.businessName,
                "phone" to settings.phone,
                "contactPhone" to settings.phone,
                "whatsappNumber" to settings.whatsappNumber,
                "email" to settings.email,
                "gstNumber" to settings.gstNumber,
                "gstin" to settings.gstNumber,
                "address" to settings.address,
                "shopAddress" to settings.address,
                "city" to settings.city,
                "state" to settings.state,
                "pincode" to settings.pincode,
                "logoUrl" to settings.logoUrl,

                // Payment Settings
                "codEnabled" to settings.codEnabled,
                "qrPaymentEnabled" to settings.qrPaymentEnabled,
                "upiEnabled" to settings.qrPaymentEnabled,
                "upiId" to settings.upiId,
                "paymentInstructions" to settings.paymentInstructions,
                "qrImageUrl" to settings.qrImageUrl,

                // Ordering Rules
                "defaultDeliveryCharge" to settings.defaultDeliveryCharge,
                "deliveryFee" to settings.defaultDeliveryCharge,
                "minimumOrderAmount" to settings.minimumOrderAmount,
                "maximumOrderAmount" to settings.maximumOrderAmount,
                "allowOutOfStockOrders" to settings.allowOutOfStockOrders,

                // Notification Preferences
                "newOrderNotification" to settings.newOrderNotification,
                "lowStockNotification" to settings.lowStockNotification,
                "paymentNotification" to settings.paymentNotification,

                // Meta
                "updatedAt" to now
            )

            // Save to primary document
            settingsCollection.document(PRIMARY_DOC_ID).set(data, SetOptions.merge()).await()
            // Also keep default_settings in sync if queried by legacy clients
            settingsCollection.document("default_settings").set(data, SetOptions.merge()).await()

            Log.d(TAG, "Saved business settings to Firestore successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save business settings to Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun DocumentSnapshot.toBusinessSettingsEntity(): BusinessSettingsEntity {
        val businessName = getString("businessName") ?: "Anu Tools & Service Center"
        val phone = getString("phone") ?: getString("contactPhone") ?: "+91 98765 43210"
        val whatsappNumber = getString("whatsappNumber") ?: phone
        val email = getString("email") ?: "contact@anutools.com"
        val gstNumber = getString("gstNumber") ?: getString("gstin") ?: ""
        val address = getString("address") ?: getString("shopAddress") ?: ""
        val city = getString("city") ?: ""
        val state = getString("state") ?: ""
        val pincode = getString("pincode") ?: ""
        val logoUrl = getString("logoUrl") ?: ""

        val codEnabled = getBoolean("codEnabled") ?: true
        val upiEnabled = getBoolean("upiEnabled") ?: getBoolean("qrPaymentEnabled") ?: true
        val qrImageUrl = getString("qrImageUrl") ?: ""
        val upiId = getString("upiId") ?: "anutools@okaxis"
        val paymentInstructions = getString("paymentInstructions")
            ?: "Scan UPI QR code or pay to UPI ID. Enter Order Number in payment remarks for immediate verification."

        val deliveryFee = getDouble("deliveryFee")
            ?: getDouble("defaultDeliveryCharge")
            ?: (getLong("deliveryFee")?.toDouble())
            ?: (getLong("defaultDeliveryCharge")?.toDouble())
            ?: 150.0

        val minOrder = getDouble("minimumOrderAmount")
            ?: (getLong("minimumOrderAmount")?.toDouble())
            ?: 1000.0

        val maxOrder = getDouble("maximumOrderAmount")
            ?: (getLong("maximumOrderAmount")?.toDouble())
            ?: 500000.0

        val allowOutOfStock = getBoolean("allowOutOfStockOrders") ?: false
        val newOrderNotif = getBoolean("newOrderNotification") ?: true
        val lowStockNotif = getBoolean("lowStockNotification") ?: true
        val payNotif = getBoolean("paymentNotification") ?: true

        val updatedAt = when (val u = get("updatedAt")) {
            is Number -> u.toLong()
            is com.google.firebase.Timestamp -> u.toDate().time
            else -> System.currentTimeMillis()
        }

        return BusinessSettingsEntity(
            settingsId = "default_settings",
            businessName = businessName,
            phone = phone,
            whatsappNumber = whatsappNumber,
            email = email,
            address = address,
            city = city,
            state = state,
            pincode = pincode,
            gstNumber = gstNumber,
            logoUrl = logoUrl,
            codEnabled = codEnabled,
            qrPaymentEnabled = upiEnabled,
            qrImageUrl = qrImageUrl,
            upiId = upiId,
            paymentInstructions = paymentInstructions,
            defaultDeliveryCharge = deliveryFee,
            minimumOrderAmount = minOrder,
            maximumOrderAmount = maxOrder,
            allowOutOfStockOrders = allowOutOfStock,
            newOrderNotification = newOrderNotif,
            lowStockNotification = lowStockNotif,
            paymentNotification = payNotif,
            updatedAt = updatedAt
        )
    }
}
