package com.example.util

import com.example.data.model.BrandEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.ProductEntity
import com.example.data.model.StockStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID

data class CsvRowValidation(
    val rowNumber: Int,
    val rawSku: String,
    val rawName: String,
    val rawShortDescription: String = "",
    val rawDescription: String = "",
    val isValid: Boolean,
    val errors: List<String>,
    val isDuplicateInCsv: Boolean = false,
    val isDuplicateInCatalog: Boolean = false,
    val product: ProductEntity? = null
)

data class CsvImportAnalysis(
    val totalRows: Int,
    val validRows: List<ProductEntity>,
    val invalidRows: List<CsvRowValidation>,
    val duplicateRows: List<CsvRowValidation>,
    val allRows: List<CsvRowValidation>
) {
    val validCount: Int get() = validRows.size
    val invalidCount: Int get() = invalidRows.size
    val duplicateCount: Int get() = duplicateRows.size
}

object CsvProductParser {

    /**
     * Cleans and normalizes header keys for robust, case-insensitive, punctuation-agnostic matching.
     * Strips UTF-8 BOM, quotes, whitespace, dashes, underscores, and non-alphanumeric chars.
     */
    fun normalizeHeader(header: String): String {
        return header
            .replace("\uFEFF", "")
            .replace("\"", "")
            .replace("'", "")
            .trim()
            .lowercase()
            .replace("[^a-z0-9]".toRegex(), "")
    }

    /**
     * Cleans cell values by stripping leading/trailing whitespace, UTF-8 BOM, surrounding quotes,
     * and unescaping RFC-4180 double quotes.
     */
    fun cleanCellValue(raw: String): String {
        var v = raw.trim()
        if (v.startsWith("\uFEFF")) {
            v = v.substring(1).trim()
        }
        if (v.startsWith("\"") && v.endsWith("\"") && v.length >= 2) {
            v = v.substring(1, v.length - 1).trim()
        } else if (v.startsWith("'") && v.endsWith("'") && v.length >= 2) {
            v = v.substring(1, v.length - 1).trim()
        }
        return v.replace("\"\"", "\"")
    }

    /**
     * Full RFC 4180 CSV parser that parses an entire stream character-by-character.
     * Accurately supports:
     * 1. Multiline fields (embedded newlines in descriptions inside quotes).
     * 2. Commas inside quotes.
     * 3. Escaped quotes ("").
     * 4. Mixed CRLF and LF line breaks.
     */
    fun parseCsvRecords(inputStream: InputStream): List<List<String>> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val records = mutableListOf<List<String>>()
        val currentRecord = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false

        var ch = reader.read()
        while (ch != -1) {
            val c = ch.toChar()
            if (c == '\"') {
                if (inQuotes) {
                    reader.mark(1)
                    val next = reader.read()
                    if (next != -1 && next.toChar() == '\"') {
                        // Escaped double quote
                        currentField.append('\"')
                    } else {
                        inQuotes = false
                        if (next != -1) {
                            reader.reset()
                        }
                    }
                } else {
                    inQuotes = true
                }
            } else if (c == ',' && !inQuotes) {
                currentRecord.add(cleanCellValue(currentField.toString()))
                currentField.clear()
            } else if ((c == '\n' || c == '\r') && !inQuotes) {
                if (c == '\r') {
                    reader.mark(1)
                    val next = reader.read()
                    if (next != -1 && next.toChar() != '\n') {
                        reader.reset()
                    }
                }
                currentRecord.add(cleanCellValue(currentField.toString()))
                currentField.clear()
                if (currentRecord.any { it.isNotBlank() }) {
                    records.add(currentRecord.toList())
                }
                currentRecord.clear()
            } else {
                currentField.append(c)
            }
            ch = reader.read()
        }

        if (currentField.isNotEmpty() || currentRecord.isNotEmpty()) {
            currentRecord.add(cleanCellValue(currentField.toString()))
            if (currentRecord.any { it.isNotBlank() }) {
                records.add(currentRecord.toList())
            }
        }

        return records
    }

    /**
     * Splits a single CSV line respecting RFC 4180 quotation rules (backward compatibility helper).
     */
    fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    sb.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                tokens.add(cleanCellValue(sb.toString()))
                sb.clear()
            } else {
                sb.append(c)
            }
            i++
        }
        tokens.add(cleanCellValue(sb.toString()))
        return tokens
    }

    /**
     * Parses and validates a CSV stream against requirements and existing catalog.
     */
    fun parseAndValidate(
        inputStream: InputStream,
        existingProducts: List<ProductEntity>,
        existingCategories: List<CategoryEntity>,
        existingBrands: List<BrandEntity>,
        adminName: String = "Admin"
    ): CsvImportAnalysis {
        val records = parseCsvRecords(inputStream)
        if (records.isEmpty()) {
            return CsvImportAnalysis(0, emptyList(), emptyList(), emptyList(), emptyList())
        }

        // Header mapping
        val headerTokens = records.first()
        val headerMap = mutableMapOf<String, Int>()
        headerTokens.forEachIndexed { index, rawHeader ->
            val norm = normalizeHeader(rawHeader)
            headerMap[norm] = index
        }

        // Sets for duplicate detection
        val existingCatalogSkus = existingProducts.map { it.sku.trim().uppercase() }.toSet()
        val seenCsvSkus = mutableMapOf<String, Int>() // sku -> first row number

        val allResults = mutableListOf<CsvRowValidation>()
        val validProducts = mutableListOf<ProductEntity>()
        val invalidRows = mutableListOf<CsvRowValidation>()
        val duplicateRows = mutableListOf<CsvRowValidation>()

        val now = System.currentTimeMillis()

        for (lineIdx in 1 until records.size) {
            val tokens = records[lineIdx]
            if (tokens.all { it.isBlank() }) continue

            val rowNumber = lineIdx + 1 // 1-based index (Header is row 1)

            fun getValue(vararg keys: String): String {
                for (k in keys) {
                    val idx = headerMap[normalizeHeader(k)]
                    if (idx != null && idx < tokens.size) {
                        val v = tokens[idx].trim()
                        if (v.isNotEmpty()) return v
                    }
                }
                return ""
            }

            // CSV Columns explicitly extracted
            val rawName = getValue("name", "productName", "title")
            val rawSku = getValue("sku", "itemSku", "productSku", "code", "partNumber", "model")
            val rawDesc = getValue("description", "desc", "fullDescription", "details")
            val rawShortDesc = getValue("shortDescription", "shortDesc", "subtitle")
            val rawSellingPrice = getValue("sellingPrice", "price", "salePrice")
            val rawRetailerPrice = getValue("retailerPrice", "wholesalePrice", "b2bPrice", "dealerPrice")
            val rawMrp = getValue("mrp", "maxRetailPrice", "maximumRetailPrice")
            val rawGst = getValue("gst", "gstPercentage", "tax", "gstPercent")
            val rawStockQuantity = getValue("stockQuantity", "stock", "quantity", "qty")
            val rawMinStock = getValue("minStock", "minimumStock", "minStockLevel")
            val rawUnit = getValue("unit", "uom")
            val rawFeatured = getValue("featured", "isFeatured")
            val rawActive = getValue("active", "isActive", "status")

            // Technical Specs columns
            val rawVoltage = getValue("voltage", "volts")
            val rawCordless = getValue("cordlessOrCorded", "cordlessCorded", "cordless", "corded", "powerSource", "type")
            val rawMotorType = getValue("motorType", "motor")

            // Image columns
            val rawImageUrl = getValue("imageUrl", "image", "images", "imageLink", "photo")
            val rawThumbnailUrl = getValue("thumbnailUrl", "thumbnail", "thumb", "thumbUrl")

            // Optional category / brand columns
            val rawCategoryId = getValue("categoryId", "catId")
            val rawCategoryName = getValue("categoryName", "category")
            val rawBrandId = getValue("brandId")
            val rawBrandName = getValue("brandName", "brand")

            // IMPORTANT DEWALT CSV ISSUE:
            // The current DEWALT CSV has the actual product title in "shortDescription" while some rows have "name" incorrectly set to "Drill Driver".
            // When importing this specific CSV, if "name" is exactly "Drill Driver" and "shortDescription" contains the actual detailed product title,
            // use the shortDescription value as the product name.
            // Otherwise preserve the CSV "name" normally.
            // Firestore must receive name, shortDescription, and description as separate fields.
            val finalName = if (rawName.equals("Drill Driver", ignoreCase = true) && rawShortDesc.isNotBlank()) {
                rawShortDesc
            } else {
                rawName
            }
            val finalShortDesc = rawShortDesc
            val finalDesc = rawDesc

            val errors = mutableListOf<String>()

            // 1. Name validation
            if (finalName.isBlank()) {
                errors.add("Product name is required")
            }

            // 2. SKU validation
            if (rawSku.isBlank()) {
                errors.add("SKU is required")
            }

            val normSku = rawSku.trim().uppercase()
            var isDuplicateInCsv = false
            var isDuplicateInCatalog = false

            if (normSku.isNotBlank()) {
                // Check duplicate within CSV
                if (seenCsvSkus.containsKey(normSku)) {
                    isDuplicateInCsv = true
                    val firstRow = seenCsvSkus[normSku]
                    errors.add("Duplicate SKU '$rawSku' in CSV (already in row $firstRow)")
                } else {
                    seenCsvSkus[normSku] = rowNumber
                }

                // Check conflict with existing database catalog
                if (existingCatalogSkus.contains(normSku)) {
                    isDuplicateInCatalog = true
                    errors.add("SKU '$rawSku' already exists in catalog")
                }
            }

            // 3. Selling Price validation
            val sellingPrice = rawSellingPrice.toDoubleOrNull()
            if (sellingPrice == null) {
                errors.add("Selling price must be a valid number (got '$rawSellingPrice')")
            } else if (sellingPrice < 0) {
                errors.add("Selling price cannot be negative ($sellingPrice)")
            }

            // 4. Retailer Price validation
            val retailerPrice = rawRetailerPrice.toDoubleOrNull()
            if (retailerPrice == null) {
                errors.add("Retailer price must be a valid number (got '$rawRetailerPrice')")
            } else if (retailerPrice < 0) {
                errors.add("Retailer price cannot be negative ($retailerPrice)")
            }

            // 5. MRP validation
            val mrp = if (rawMrp.isBlank()) {
                sellingPrice ?: 0.0
            } else {
                val parsedMrp = rawMrp.toDoubleOrNull()
                if (parsedMrp == null) {
                    errors.add("MRP must be a valid number (got '$rawMrp')")
                    0.0
                } else if (parsedMrp < 0) {
                    errors.add("MRP cannot be negative ($parsedMrp)")
                    parsedMrp
                } else {
                    parsedMrp
                }
            }

            // 6. Stock Quantity validation
            val stockQuantity = if (rawStockQuantity.isBlank()) {
                0
            } else {
                val parsedStock = rawStockQuantity.toIntOrNull()
                if (parsedStock == null) {
                    errors.add("Stock quantity must be an integer (got '$rawStockQuantity')")
                    0
                } else if (parsedStock < 0) {
                    errors.add("Stock quantity cannot be negative ($parsedStock)")
                    parsedStock
                } else {
                    parsedStock
                }
            }

            // 7. GST Percentage validation
            val gstPercentage = if (rawGst.isBlank()) {
                18.0
            } else {
                val parsedGst = rawGst.toDoubleOrNull()
                if (parsedGst == null) {
                    errors.add("GST must be a valid number (got '$rawGst')")
                    18.0
                } else if (parsedGst < 0) {
                    errors.add("GST cannot be negative ($parsedGst)")
                    parsedGst
                } else {
                    parsedGst
                }
            }

            // 8. Min Stock validation (defaults to 0 per requirements)
            val minStock = if (rawMinStock.isBlank()) {
                0
            } else {
                val parsedMin = rawMinStock.toIntOrNull()
                if (parsedMin == null || parsedMin < 0) {
                    0
                } else {
                    parsedMin
                }
            }

            // Defaults
            val unit = if (rawUnit.isNotBlank()) rawUnit else "Piece"
            val active = when (rawActive.lowercase()) {
                "false", "0", "no", "inactive" -> false
                else -> true // Defaults to true
            }
            val featured = when (rawFeatured.lowercase()) {
                "true", "1", "yes" -> true
                else -> false // Defaults to false
            }

            // Brand resolution (auto-detect DeWalt if brand column absent)
            val matchedBrand = existingBrands.firstOrNull {
                it.name.equals(rawBrandName, ignoreCase = true) ||
                it.brandId.equals(rawBrandId, ignoreCase = true)
            } ?: run {
                if (rawBrandName.isBlank()) {
                    val fullText = "$rawSku $finalName $finalShortDesc $finalDesc"
                    if (fullText.contains("dewalt", ignoreCase = true) ||
                        rawSku.startsWith("DW", ignoreCase = true) ||
                        rawSku.startsWith("DCD", ignoreCase = true) ||
                        rawSku.startsWith("DCF", ignoreCase = true) ||
                        rawSku.startsWith("DCG", ignoreCase = true)) {
                        existingBrands.firstOrNull { it.name.equals("Dewalt", ignoreCase = true) || it.brandId.contains("dewalt", ignoreCase = true) }
                    } else null
                } else null
            }
            val resolvedBrandId = matchedBrand?.brandId
                ?: rawBrandId.ifBlank { if (finalName.contains("dewalt", ignoreCase = true)) "br_dewalt" else "brand_generic" }
            val resolvedBrandName = matchedBrand?.name
                ?: rawBrandName.ifBlank { if (finalName.contains("dewalt", ignoreCase = true)) "Dewalt" else "Generic" }

            // Category resolution (auto-detect Power Tools if category column absent)
            val matchedCategory = existingCategories.firstOrNull {
                it.name.equals(rawCategoryName, ignoreCase = true) ||
                it.categoryId.equals(rawCategoryId, ignoreCase = true)
            } ?: run {
                if (rawCategoryName.isBlank()) {
                    val fullText = "$finalName $finalShortDesc $finalDesc".lowercase()
                    if (fullText.contains("drill") || fullText.contains("driver") || fullText.contains("grinder") ||
                        fullText.contains("saw") || fullText.contains("impact") || fullText.contains("power tool") ||
                        fullText.contains("hammer") || fullText.contains("blower") || fullText.contains("cutter")) {
                        existingCategories.firstOrNull { it.name.equals("Power Tools", ignoreCase = true) || it.categoryId == "cat_power_tools" }
                    } else null
                } else null
            }
            val resolvedCategoryId = matchedCategory?.categoryId
                ?: rawCategoryId.ifBlank { "cat_power_tools" }
            val resolvedCategoryName = matchedCategory?.name
                ?: rawCategoryName.ifBlank { "Power Tools" }

            // Specifications JSON builder
            val specsObj = JSONObject()
            if (rawVoltage.isNotBlank()) specsObj.put("Voltage", rawVoltage)
            if (rawCordless.isNotBlank()) specsObj.put("Cordless/Corded", rawCordless)
            if (rawMotorType.isNotBlank()) specsObj.put("Motor Type", rawMotorType)
            val specificationsJson = specsObj.toString()

            // Images and Thumbnail builder
            val effectiveThumbnail = rawThumbnailUrl.ifBlank { rawImageUrl }
            val imagesList = mutableListOf<String>()
            if (rawImageUrl.isNotBlank()) {
                val urls = rawImageUrl.split(",", ";", "|").map { it.trim() }.filter { it.isNotBlank() }
                imagesList.addAll(urls)
            }
            if (rawThumbnailUrl.isNotBlank() && !imagesList.contains(rawThumbnailUrl)) {
                imagesList.add(0, rawThumbnailUrl)
            }
            val imagesJson = JSONArray(imagesList).toString()

            // Slug & Product ID generation
            val slug = finalName.lowercase().replace("[^a-z0-9]+".toRegex(), "-").trim('-')
            val uniqueSuffix = UUID.randomUUID().toString().replace("-", "").take(8)
            val generatedProductId = "prod_$uniqueSuffix"

            val isValid = errors.isEmpty()
            val productEntity = if (isValid) {
                val calcStatus = ProductEntity.calculateStockStatus(stockQuantity, minStock)
                ProductEntity(
                    productId = generatedProductId,
                    name = finalName,
                    slug = slug,
                    sku = rawSku,
                    brandId = resolvedBrandId,
                    brandName = resolvedBrandName,
                    categoryId = resolvedCategoryId,
                    categoryName = resolvedCategoryName,
                    subcategoryId = "",
                    subcategoryName = "",
                    shortDescription = finalShortDesc,
                    description = finalDesc,
                    specificationsJson = specificationsJson,
                    mrp = mrp,
                    sellingPrice = sellingPrice ?: 0.0,
                    retailerPrice = retailerPrice ?: 0.0,
                    discount = if (mrp > 0 && sellingPrice != null && mrp > sellingPrice) ((mrp - sellingPrice) / mrp) * 100.0 else 0.0,
                    gstPercentage = gstPercentage,
                    stockQuantity = stockQuantity,
                    minimumStockLevel = minStock,
                    unit = unit,
                    stockStatus = calcStatus,
                    imagesJson = imagesJson,
                    thumbnailUrl = effectiveThumbnail,
                    active = active,
                    featured = featured,
                    createdAt = now,
                    updatedAt = now,
                    createdBy = adminName,
                    updatedBy = adminName
                )
            } else {
                null
            }

            val validation = CsvRowValidation(
                rowNumber = rowNumber,
                rawSku = rawSku,
                rawName = finalName,
                rawShortDescription = finalShortDesc,
                rawDescription = finalDesc,
                isValid = isValid,
                errors = errors,
                isDuplicateInCsv = isDuplicateInCsv,
                isDuplicateInCatalog = isDuplicateInCatalog,
                product = productEntity
            )

            allResults.add(validation)
            if (isValid && productEntity != null) {
                validProducts.add(productEntity)
            } else {
                invalidRows.add(validation)
                if (isDuplicateInCsv || isDuplicateInCatalog) {
                    duplicateRows.add(validation)
                }
            }
        }

        return CsvImportAnalysis(
            totalRows = allResults.size,
            validRows = validProducts,
            invalidRows = invalidRows,
            duplicateRows = duplicateRows,
            allRows = allResults
        )
    }

    /**
     * Generates a sample CSV template with all required columns and example rows.
     */
    fun generateSampleCsvTemplate(): String {
        return buildString {
            appendLine("name,sku,description,shortDescription,sellingPrice,retailerPrice,mrp,gst,stockQuantity,minStock,unit,featured,active,voltage,cordlessOrCorded,motorType,imageUrl,thumbnailUrl")
            appendLine("\"Dewalt 18V XR Li-Ion Compact Brushless Drill Driver\",DEWALT-DCD777,\"High performance brushless motor delivers 65Nm of torque with 15 position adjustable torque control and 2-speed all metal transmission.\",\"Dewalt 18V XR Li-Ion Compact Brushless Drill Driver\",4500.0,3800.0,5200.0,18.0,20,5,Piece,true,true,18V,Cordless,Brushless,\"https://example.com/dcd777.jpg\",\"https://example.com/dcd777_thumb.jpg\"")
            appendLine("\"Bosch Professional Angle Grinder GWS 600\",BOSCH-AG-600,\"High quality compact 670W angle grinder for cutting and grinding metal and masonry.\",\"Compact 670W 100mm Angle Grinder\",2199.0,1950.0,2499.0,18.0,25,5,Piece,true,true,220V,Corded,Carbon Brush,\"\",\"\"")
            appendLine("\"Dewalt 13mm Impact Drill DWD024\",DEWALT-ID-13,\"650W robust impact drill with variable speed trigger and ergonomic rubber grip.\",\"650W Corded Impact Drill 13mm\",3299.0,2990.0,3850.0,18.0,15,3,Piece,false,true,220V,Corded,Carbon Brush,\"\",\"\"")
        }
    }
}

