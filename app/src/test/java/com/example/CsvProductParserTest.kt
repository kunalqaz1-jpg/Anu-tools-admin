package com.example

import com.example.data.model.BrandEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.ProductEntity
import com.example.util.CsvProductParser
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class CsvProductParserTest {

    @Test
    fun `test valid csv parsing and default values`() {
        val csv = """
            name,sku,categoryId,categoryName,brandId,brandName,mrp,sellingPrice,retailerPrice,gst,stockQuantity,minStock,unit,description,shortDescription,featured,active
            "Bosch Professional Angle Grinder",BOSCH-AG-01,cat_power,Power Tools,brand_bosch,Bosch,2500,2200,1950,18,20,5,Piece,"Heavy duty grinder","Angle Grinder",true,true
            "Basic Wire Stripper",TOOL-WS-02,,,,,,150,120,,,,,,,false,false
        """.trimIndent()

        val existingProducts = emptyList<ProductEntity>()
        val existingCats = listOf(CategoryEntity(categoryId = "cat_power", name = "Power Tools", slug = "power-tools"))
        val existingBrands = listOf(BrandEntity(brandId = "brand_bosch", name = "Bosch", slug = "bosch"))

        val analysis = CsvProductParser.parseAndValidate(
            inputStream = ByteArrayInputStream(csv.toByteArray()),
            existingProducts = existingProducts,
            existingCategories = existingCats,
            existingBrands = existingBrands,
            adminName = "TestAdmin"
        )

        assertEquals(2, analysis.totalRows)
        assertEquals(2, analysis.validCount)
        assertEquals(0, analysis.invalidCount)
        assertEquals(0, analysis.duplicateCount)

        val prod1 = analysis.validRows[0]
        assertEquals("Bosch Professional Angle Grinder", prod1.name)
        assertEquals("BOSCH-AG-01", prod1.sku)
        assertEquals(2200.0, prod1.sellingPrice, 0.01)
        assertEquals(1950.0, prod1.retailerPrice, 0.01)
        assertEquals(2500.0, prod1.mrp, 0.01)
        assertEquals(20, prod1.stockQuantity)
        assertEquals(5, prod1.minimumStockLevel)
        assertEquals("Piece", prod1.unit)
        assertTrue(prod1.featured)
        assertTrue(prod1.active)

        val prod2 = analysis.validRows[1]
        assertEquals("Basic Wire Stripper", prod2.name)
        assertEquals("TOOL-WS-02", prod2.sku)
        assertEquals(150.0, prod2.sellingPrice, 0.01)
        assertEquals(120.0, prod2.retailerPrice, 0.01)
        assertEquals(0, prod2.stockQuantity)
        assertEquals(0, prod2.minimumStockLevel) // minStock defaults to 0
        assertEquals("Piece", prod2.unit) // unit defaults to "Piece"
        assertFalse(prod2.featured) // featured defaults to false
        assertFalse(prod2.active) // explicitly set to false
    }

    @Test
    fun `test validation catches required fields and negative values`() {
        val csv = """
            name,sku,sellingPrice,retailerPrice,mrp,stockQuantity,gst
            ,SKU-NO-NAME,100,80,120,5,18
            Valid Name,,100,80,120,5,18
            Bad Price,SKU-NEG-PRICE,-50,80,120,5,18
            Bad Stock,SKU-NEG-STOCK,100,80,120,-10,18
        """.trimIndent()

        val analysis = CsvProductParser.parseAndValidate(
            inputStream = ByteArrayInputStream(csv.toByteArray()),
            existingProducts = emptyList(),
            existingCategories = emptyList(),
            existingBrands = emptyList()
        )

        assertEquals(4, analysis.totalRows)
        assertEquals(0, analysis.validCount)
        assertEquals(4, analysis.invalidCount)

        assertTrue(analysis.invalidRows[0].errors.any { it.contains("name is required", ignoreCase = true) })
        assertTrue(analysis.invalidRows[1].errors.any { it.contains("SKU is required", ignoreCase = true) })
        assertTrue(analysis.invalidRows[2].errors.any { it.contains("Selling price cannot be negative", ignoreCase = true) })
        assertTrue(analysis.invalidRows[3].errors.any { it.contains("Stock quantity cannot be negative", ignoreCase = true) })
    }

    @Test
    fun `test duplicate sku detection inside csv and against catalog`() {
        val existingProd = ProductEntity(
            productId = "prod_existing",
            name = "Existing In Catalog",
            slug = "existing-in-catalog",
            sku = "CATALOG-SKU-99",
            brandId = "brand_1",
            brandName = "Brand",
            categoryId = "cat_1",
            categoryName = "Cat",
            mrp = 100.0,
            sellingPrice = 90.0,
            retailerPrice = 80.0
        )

        val csv = """
            name,sku,sellingPrice,retailerPrice
            Item One,DUPLICATE-SKU,100,80
            Item Two,DUPLICATE-SKU,200,160
            Item Three,CATALOG-SKU-99,300,240
        """.trimIndent()

        val analysis = CsvProductParser.parseAndValidate(
            inputStream = ByteArrayInputStream(csv.toByteArray()),
            existingProducts = listOf(existingProd),
            existingCategories = emptyList(),
            existingBrands = emptyList()
        )

        assertEquals(3, analysis.totalRows)
        assertEquals(1, analysis.validCount) // Item One is valid
        assertEquals(2, analysis.invalidCount) // Item Two (CSV duplicate) & Item Three (catalog conflict)
        assertEquals(2, analysis.duplicateCount)

        assertTrue(analysis.invalidRows.any { it.isDuplicateInCsv })
        assertTrue(analysis.invalidRows.any { it.isDuplicateInCatalog })
    }

    @Test
    fun `test template generation is non-empty and has required columns`() {
        val template = CsvProductParser.generateSampleCsvTemplate()
        assertTrue(template.contains("name,sku,description,shortDescription"))
        assertTrue(template.contains("sellingPrice,retailerPrice,gst,stockQuantity,minStock,unit"))
        val lines = template.trim().lines()
        assertTrue(lines.size >= 2) // Header + sample items
    }

    @Test
    fun `test dewalt csv mapping with drill driver title replacement and firestore fields`() {
        val csv = """
            name,sku,description,shortDescription,sellingPrice,retailerPrice,mrp,gst,stockQuantity,minStock,unit,featured,active,voltage,cordlessOrCorded,motorType,imageUrl,thumbnailUrl
            "Drill Driver",DCD777D2,"Compact brushless drill driver featuring 15 position torque settings and 2 speed transmission.","DEWALT 18V XR Brushless Compact Drill Driver DCD777D2",7500,6800,9200,18,12,3,Piece,true,true,18V,Cordless,Brushless,"https://res.cloudinary.com/demo/image/upload/dcd777.jpg","https://res.cloudinary.com/demo/image/upload/dcd777_thumb.jpg"
            "Dewalt 125mm Angle Grinder DWE4114",DWE4114,"900W heavy duty angle grinder with dust ejection system.","Dewalt 125mm Corded Angle Grinder",4200,3800,5100,18,25,5,Piece,false,true,220V,Corded,Carbon Brush,"https://res.cloudinary.com/demo/image/upload/dwe4114.jpg",""
        """.trimIndent()

        val analysis = CsvProductParser.parseAndValidate(
            inputStream = ByteArrayInputStream(csv.toByteArray()),
            existingProducts = emptyList(),
            existingCategories = emptyList(),
            existingBrands = emptyList()
        )

        assertEquals(2, analysis.totalRows)
        assertEquals(2, analysis.validCount)
        assertEquals(0, analysis.invalidCount)

        // Row 1: "Drill Driver" replaced by shortDescription because name was "Drill Driver"
        val row1 = analysis.validRows[0]
        assertEquals("DEWALT 18V XR Brushless Compact Drill Driver DCD777D2", row1.name)
        assertEquals("DEWALT 18V XR Brushless Compact Drill Driver DCD777D2", row1.shortDescription)
        assertEquals("Compact brushless drill driver featuring 15 position torque settings and 2 speed transmission.", row1.description)
        assertEquals("DCD777D2", row1.sku)
        assertEquals(7500.0, row1.sellingPrice, 0.01)
        assertEquals(6800.0, row1.retailerPrice, 0.01)
        assertEquals(9200.0, row1.mrp, 0.01)
        assertEquals(18.0, row1.gstPercentage, 0.01)
        assertEquals(12, row1.stockQuantity)
        assertEquals(3, row1.minimumStockLevel)
        assertEquals("Piece", row1.unit)
        assertTrue(row1.featured)
        assertTrue(row1.active)
        assertEquals("Dewalt", row1.brandName)
        assertEquals("https://res.cloudinary.com/demo/image/upload/dcd777_thumb.jpg", row1.thumbnailUrl)
        assertTrue(row1.specificationsJson.contains("18V"))
        assertTrue(row1.specificationsJson.contains("Cordless"))
        assertTrue(row1.specificationsJson.contains("Brushless"))

        // Row 2: Normal name preserved
        val row2 = analysis.validRows[1]
        assertEquals("Dewalt 125mm Angle Grinder DWE4114", row2.name)
        assertEquals("Dewalt 125mm Corded Angle Grinder", row2.shortDescription)
        assertEquals("900W heavy duty angle grinder with dust ejection system.", row2.description)
        assertEquals("DWE4114", row2.sku)
        assertEquals(4200.0, row2.sellingPrice, 0.01)
        assertEquals("https://res.cloudinary.com/demo/image/upload/dwe4114.jpg", row2.thumbnailUrl)

        // Check Firestore mapping logic from FirestoreProductRepository
        val firestoreRepo = com.example.data.remote.FirestoreProductRepository()
        val firestoreMap = with(firestoreRepo) { row1.toFirestoreMap() }

        assertEquals("DEWALT 18V XR Brushless Compact Drill Driver DCD777D2", firestoreMap["name"])
        assertEquals("DEWALT 18V XR Brushless Compact Drill Driver DCD777D2", firestoreMap["shortDescription"])
        assertEquals("Compact brushless drill driver featuring 15 position torque settings and 2 speed transmission.", firestoreMap["description"])
        assertEquals("DCD777D2", firestoreMap["sku"])
        assertEquals(7500.0, firestoreMap["sellingPrice"])
        assertEquals(6800.0, firestoreMap["retailerPrice"])
        assertEquals(9200.0, firestoreMap["mrp"])
        assertEquals(18.0, firestoreMap["gst"])
        assertEquals(12, firestoreMap["stockQuantity"])
        assertEquals(3, firestoreMap["minStock"])
        assertEquals("18V", firestoreMap["voltage"])
        assertEquals("Cordless", firestoreMap["cordlessOrCorded"])
        assertEquals("Brushless", firestoreMap["motorType"])
    }
}
