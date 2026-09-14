package com.example.data.remote

import android.util.Log
import com.example.data.model.BrandEntity
import com.example.data.model.CategoryEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore repository for Categories and Brands.
 *
 * Collections:
 *  • "categories" — readable by the public retailer/customer app
 *  • "brands"     — readable by the public retailer/customer app
 *
 * Admin writes are allowed through the admin app (authenticated user).
 * Public read access should be granted in Firestore security rules (allow read: if true;).
 */
class FirestoreCatalogRepository {

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val categoriesCollection get() = firestore.collection("categories")
    private val brandsCollection get() = firestore.collection("brands")

    companion object {
        private const val TAG = "FirestoreCatalogRepo"
    }

    // ──────────────────────────────────────────────────────────
    // CATEGORIES
    // ──────────────────────────────────────────────────────────

    /** Real-time stream of all categories from Firestore. */
    val categoriesFlow: Flow<List<CategoryEntity>> = callbackFlow {
        val reg = categoriesCollection
            .orderBy("sortOrder")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "Error fetching categories", err)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    runCatching {
                        CategoryEntity(
                            categoryId = doc.getString("categoryId") ?: doc.id,
                            name = doc.getString("name") ?: "",
                            slug = doc.getString("slug") ?: "",
                            imageUrl = doc.getString("imageUrl") ?: "",
                            description = doc.getString("description") ?: "",
                            active = doc.getBoolean("active") ?: true,
                            sortOrder = (doc.getLong("sortOrder") ?: 0L).toInt(),
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                        )
                    }.getOrNull()
                } ?: emptyList()
                Log.d(TAG, "Loaded ${list.size} categories from Firestore")
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    /** Upsert a single category to Firestore. */
    suspend fun saveCategory(category: CategoryEntity): Result<Unit> = runCatching {
        val data = mapOf(
            "categoryId" to category.categoryId,
            "name" to category.name,
            "slug" to category.slug,
            "imageUrl" to category.imageUrl,
            "description" to category.description,
            "active" to category.active,
            "sortOrder" to category.sortOrder,
            "createdAt" to category.createdAt,
            "updatedAt" to System.currentTimeMillis()
        )
        categoriesCollection.document(category.categoryId).set(data, SetOptions.merge()).await()
        Log.d(TAG, "Category ${category.categoryId} saved to Firestore")
    }

    /** Delete a category from Firestore. */
    suspend fun deleteCategory(categoryId: String): Result<Unit> = runCatching {
        categoriesCollection.document(categoryId).delete().await()
        Log.d(TAG, "Category $categoryId deleted from Firestore")
    }

    /** Seed initial categories into Firestore if the collection is empty. */
    suspend fun seedCategoriesIfEmpty(defaultCategories: List<CategoryEntity>) {
        try {
            val snap = categoriesCollection.limit(1).get().await()
            if (snap.isEmpty) {
                Log.d(TAG, "Seeding ${defaultCategories.size} default categories to Firestore")
                val batch = firestore.batch()
                defaultCategories.forEach { cat ->
                    val ref = categoriesCollection.document(cat.categoryId)
                    val data = mapOf(
                        "categoryId" to cat.categoryId,
                        "name" to cat.name,
                        "slug" to cat.slug,
                        "imageUrl" to cat.imageUrl,
                        "description" to cat.description,
                        "active" to cat.active,
                        "sortOrder" to cat.sortOrder,
                        "createdAt" to cat.createdAt,
                        "updatedAt" to cat.updatedAt
                    )
                    batch.set(ref, data)
                }
                batch.commit().await()
                Log.d(TAG, "Default categories seeded to Firestore")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not seed categories to Firestore: ${e.message}")
        }
    }

    // ──────────────────────────────────────────────────────────
    // BRANDS
    // ──────────────────────────────────────────────────────────

    /** Real-time stream of all brands from Firestore. */
    val brandsFlow: Flow<List<BrandEntity>> = callbackFlow {
        val reg = brandsCollection
            .orderBy("sortOrder")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "Error fetching brands", err)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    runCatching {
                        BrandEntity(
                            brandId = doc.getString("brandId") ?: doc.id,
                            name = doc.getString("name") ?: "",
                            slug = doc.getString("slug") ?: "",
                            logoUrl = doc.getString("logoUrl") ?: "",
                            description = doc.getString("description") ?: "",
                            active = doc.getBoolean("active") ?: true,
                            sortOrder = (doc.getLong("sortOrder") ?: 0L).toInt(),
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                        )
                    }.getOrNull()
                } ?: emptyList()
                Log.d(TAG, "Loaded ${list.size} brands from Firestore")
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    /** Upsert a single brand to Firestore. */
    suspend fun saveBrand(brand: BrandEntity): Result<Unit> = runCatching {
        val data = mapOf(
            "brandId" to brand.brandId,
            "name" to brand.name,
            "slug" to brand.slug,
            "logoUrl" to brand.logoUrl,
            "description" to brand.description,
            "active" to brand.active,
            "sortOrder" to brand.sortOrder,
            "createdAt" to brand.createdAt,
            "updatedAt" to System.currentTimeMillis()
        )
        brandsCollection.document(brand.brandId).set(data, SetOptions.merge()).await()
        Log.d(TAG, "Brand ${brand.brandId} saved to Firestore")
    }

    /** Delete a brand from Firestore. */
    suspend fun deleteBrand(brandId: String): Result<Unit> = runCatching {
        brandsCollection.document(brandId).delete().await()
        Log.d(TAG, "Brand $brandId deleted from Firestore")
    }

    /** Seed initial brands into Firestore if the collection is empty. */
    suspend fun seedBrandsIfEmpty(defaultBrands: List<BrandEntity>) {
        try {
            val snap = brandsCollection.limit(1).get().await()
            if (snap.isEmpty) {
                Log.d(TAG, "Seeding ${defaultBrands.size} default brands to Firestore")
                val batch = firestore.batch()
                defaultBrands.forEach { brand ->
                    val ref = brandsCollection.document(brand.brandId)
                    val data = mapOf(
                        "brandId" to brand.brandId,
                        "name" to brand.name,
                        "slug" to brand.slug,
                        "logoUrl" to brand.logoUrl,
                        "description" to brand.description,
                        "active" to brand.active,
                        "sortOrder" to brand.sortOrder,
                        "createdAt" to brand.createdAt,
                        "updatedAt" to brand.updatedAt
                    )
                    batch.set(ref, data)
                }
                batch.commit().await()
                Log.d(TAG, "Default brands seeded to Firestore")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not seed brands to Firestore: ${e.message}")
        }
    }
}
