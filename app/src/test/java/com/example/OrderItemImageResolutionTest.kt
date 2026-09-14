package com.example

import com.example.data.model.OrderItemSnapshot
import com.example.data.model.ProductEntity
import com.example.ui.screens.resolveOrderItemImageUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OrderItemImageResolutionTest {

    private fun createSampleProduct(
        productId: String = "prod_001",
        sku: String = "SKU-TEST-01",
        name: String = "Cordless Impact Drill",
        thumbnailUrl: String = "https://res.cloudinary.com/jg8dnjho/image/upload/v1700000000/products/drill_thumb.jpg",
        images: List<String> = listOf("https://res.cloudinary.com/jg8dnjho/image/upload/v1700000000/products/drill_main.jpg")
    ): ProductEntity {
        val imagesArr = JSONArray()
        images.forEach { imagesArr.put(it) }
        return ProductEntity(
            productId = productId,
            name = name,
            slug = "cordless-impact-drill",
            sku = sku,
            brandId = "brand_01",
            brandName = "Bosch",
            categoryId = "cat_01",
            categoryName = "Power Tools",
            mrp = 5999.0,
            sellingPrice = 4999.0,
            retailerPrice = 4500.0,
            thumbnailUrl = thumbnailUrl,
            imagesJson = imagesArr.toString()
        )
    }

    @Test
    fun testResolveOrderItemImageUrl_withCloudinaryHttpsUrl() {
        val cloudinaryUrl = "https://res.cloudinary.com/jg8dnjho/image/upload/v1789110789/products/drill.jpg"
        val resolved = resolveOrderItemImageUrl(
            itemImageUrl = cloudinaryUrl,
            product = null
        )
        assertEquals(cloudinaryUrl, resolved)
    }

    @Test
    fun testResolveOrderItemImageUrl_withMissingImageUrl_fallsBackToProductThumbnail() {
        val product = createSampleProduct(
            thumbnailUrl = "https://res.cloudinary.com/jg8dnjho/image/upload/v1700000000/products/fallback_thumb.jpg"
        )
        val resolved = resolveOrderItemImageUrl(
            itemImageUrl = "",
            product = product
        )
        assertEquals(product.thumbnailUrl, resolved)
    }

    @Test
    fun testResolveOrderItemImageUrl_withMissingImageUrlAndBlankThumbnail_fallsBackToFirstProductImage() {
        val product = createSampleProduct(
            thumbnailUrl = "",
            images = listOf("https://res.cloudinary.com/jg8dnjho/image/upload/v1700000000/products/gallery1.jpg")
        )
        val resolved = resolveOrderItemImageUrl(
            itemImageUrl = "   ",
            product = product
        )
        assertEquals("https://res.cloudinary.com/jg8dnjho/image/upload/v1700000000/products/gallery1.jpg", resolved)
    }

    @Test
    fun testResolveOrderItemImageUrl_strictlyRejectsBase64Data() {
        val base64Data = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP..."
        
        // When product has valid HTTPS fallback
        val product = createSampleProduct(
            thumbnailUrl = "https://res.cloudinary.com/jg8dnjho/image/upload/v1700000000/products/clean.jpg"
        )
        val resolvedWithFallback = resolveOrderItemImageUrl(
            itemImageUrl = base64Data,
            product = product
        )
        assertEquals(product.thumbnailUrl, resolvedWithFallback)

        // When product has base64 data too, it must NOT return base64
        val productWithBase64 = createSampleProduct(
            thumbnailUrl = "data:image/png;base64,iVBORw0KGgo...",
            images = emptyList()
        )
        val resolvedNone = resolveOrderItemImageUrl(
            itemImageUrl = base64Data,
            product = productWithBase64
        )
        assertNull(resolvedNone)
    }

    @Test
    fun testResolveOrderItemImageUrl_returnsNullWhenNoValidUrlExists() {
        val resolved = resolveOrderItemImageUrl(
            itemImageUrl = null,
            product = null
        )
        assertNull(resolved)
    }

    @Test
    fun testParseOrderItemSnapshot_extractsImageUrl() {
        val jsonItem = JSONObject().apply {
            put("productId", "prod_123")
            put("sku", "BOSCH-GSR-120")
            put("name", "Bosch GSR 120-LI")
            put("imageUrl", "https://res.cloudinary.com/jg8dnjho/image/upload/v123456/item.jpg")
            put("quantity", 2)
            put("unit", "Piece")
            put("unitPrice", 3500.0)
            put("mrp", 4200.0)
            put("gstPercentage", 18.0)
            put("gstAmount", 1260.0)
            put("subtotal", 7000.0)
        }

        val snapshot = OrderItemSnapshot(
            productId = jsonItem.optString("productId"),
            sku = jsonItem.optString("sku"),
            name = jsonItem.optString("name"),
            imageUrl = jsonItem.optString("imageUrl"),
            quantity = jsonItem.optInt("quantity", 1),
            unit = jsonItem.optString("unit", "Piece"),
            unitPrice = jsonItem.optDouble("unitPrice", 0.0),
            mrp = jsonItem.optDouble("mrp", 0.0),
            gstPercentage = jsonItem.optDouble("gstPercentage", 18.0),
            gstAmount = jsonItem.optDouble("gstAmount", 0.0),
            subtotal = jsonItem.optDouble("subtotal", 0.0)
        )

        assertEquals("https://res.cloudinary.com/jg8dnjho/image/upload/v123456/item.jpg", snapshot.imageUrl)
        assertTrue(snapshot.imageUrl.startsWith("https://res.cloudinary.com/"))
    }
}
