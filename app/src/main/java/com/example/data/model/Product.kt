package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class StockStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK
}

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val productId: String,
    val name: String,
    val slug: String,
    val sku: String,
    val brandId: String,
    val brandName: String,
    val categoryId: String,
    val categoryName: String,
    val subcategoryId: String = "",
    val subcategoryName: String = "",
    val shortDescription: String = "",
    val description: String = "",
    val specificationsJson: String = "{}",
    val mrp: Double,
    val sellingPrice: Double,
    val retailerPrice: Double,
    val discount: Double = 0.0,
    val gstPercentage: Double = 18.0,
    val stockQuantity: Int = 0,
    val minimumStockLevel: Int = 5,
    val unit: String = "Piece",
    val stockStatus: StockStatus = StockStatus.IN_STOCK,
    val imagesJson: String = "[]",
    val thumbnailUrl: String = "",
    val active: Boolean = true,
    val featured: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdBy: String = "system",
    val updatedBy: String = "system"
) {
    fun getImagesList(): List<String> {
        return try {
            val jsonArray = org.json.JSONArray(imagesJson)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val str = jsonArray.optString(i)
                if (!str.isNullOrBlank()) list.add(str)
            }
            if (list.isEmpty() && thumbnailUrl.isNotBlank()) {
                listOf(thumbnailUrl)
            } else {
                list
            }
        } catch (_: Exception) {
            if (thumbnailUrl.isNotBlank()) listOf(thumbnailUrl) else emptyList()
        }
    }

    companion object {
        fun calculateStockStatus(quantity: Int, minLevel: Int): StockStatus {
            return when {
                quantity <= 0 -> StockStatus.OUT_OF_STOCK
                quantity <= minLevel -> StockStatus.LOW_STOCK
                else -> StockStatus.IN_STOCK
            }
        }
    }
}
