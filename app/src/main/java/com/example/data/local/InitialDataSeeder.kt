package com.example.data.local

import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object InitialDataSeeder {
    suspend fun seedDatabaseIfEmpty(database: AnuToolsDatabase) = withContext(Dispatchers.IO) {
        val settingsDao = database.businessSettingsDao()
        if (settingsDao.getSettingsDirect() != null) {
            return@withContext // Database already seeded
        }

        // 1. Business Settings
        val defaultSettings = BusinessSettingsEntity(
            settingsId = "default_settings",
            businessName = "Anu Tools & Service Center",
            phone = "+91 98141 22334",
            whatsappNumber = "+91 98141 22334",
            email = "sales@anutools.com",
            address = "Shop 14-16, Industrial Tools Market, Gill Road",
            city = "Ludhiana",
            state = "Punjab",
            pincode = "141003",
            gstNumber = "03AABCA1234F1Z5",
            codEnabled = true,
            qrPaymentEnabled = true,
            upiId = "anutools@icici",
            paymentInstructions = "Scan UPI QR with Google Pay/PhonePe/Paytm. Mention Order ID in remarks. Verification done within 15 minutes.",
            defaultDeliveryCharge = 150.0,
            minimumOrderAmount = 1500.0,
            allowOutOfStockOrders = false
        )
        settingsDao.insertOrUpdate(defaultSettings)

        // 2. Users
        val users = listOf(
            UserEntity(
                userId = "usr_super_admin",
                name = "Sunil Kumar",
                email = "admin@anutools.com",
                phone = "+91 98141 22334",
                role = UserRole.SUPER_ADMIN
            ),
            UserEntity(
                userId = "usr_admin",
                name = "Anurag Sharma",
                email = "anurag@anutools.com",
                phone = "+91 98722 55667",
                role = UserRole.ADMIN
            ),
            UserEntity(
                userId = "usr_staff",
                name = "Harpreet Singh",
                email = "staff@anutools.com",
                phone = "+91 94170 88990",
                role = UserRole.STAFF
            )
        )
        database.userDao().insertAll(users)

        // 3. Categories
        val categories = listOf(
            CategoryEntity("cat_grinder", "Grinder Machine", "grinder-machine", sortOrder = 1, description = "Angle grinders, die grinders & polishers"),
            CategoryEntity("cat_cutting", "Cutting Machine", "cutting-machine", sortOrder = 2, description = "Chop saws, marble cutters, metal cut-off machines"),
            CategoryEntity("cat_drill", "Drill Machine", "drill-machine", sortOrder = 3, description = "Impact drills, rotary hammers & cordless drivers"),
            CategoryEntity("cat_welding", "Welding Machine", "welding-machine", sortOrder = 4, description = "ARC, TIG, MIG & IGBT inverter welders"),
            CategoryEntity("cat_power_tools", "Power Tools", "power-tools", sortOrder = 5, description = "Blowers, planers, routers & heat guns"),
            CategoryEntity("cat_hand_tools", "Hand Tools", "hand-tools", sortOrder = 6, description = "Spanners, socket sets, pliers & torque wrenches"),
            CategoryEntity("cat_cutting_discs", "Cutting & Grinding Discs", "discs-wheels", sortOrder = 7, description = "Abrasive discs, diamond blades & flap wheels"),
            CategoryEntity("cat_accessories", "Machine Accessories", "accessories", sortOrder = 8, description = "Drill bits, chuck keys, safety guards"),
            CategoryEntity("cat_spare_parts", "Spare Parts", "spare-parts", sortOrder = 9, description = "Carbon brushes, switches, bearings & consumables"),
            CategoryEntity("cat_coil_armature", "Coil & Armature", "coil-armature", sortOrder = 10, description = "Motor armatures, field coils & winding spares")
        )
        database.categoryDao().insertAll(categories)

        // 4. Brands
        val brands = listOf(
            BrandEntity("br_bosch", "Bosch", "bosch", sortOrder = 1, description = "German engineered professional power tools"),
            BrandEntity("br_dewalt", "Dewalt", "dewalt", sortOrder = 2, description = "Guaranteed tough construction & industrial grade"),
            BrandEntity("br_stanley", "Stanley", "stanley", sortOrder = 3, description = "Proven workshop & industrial hand/power tools"),
            BrandEntity("br_black_decker", "Black & Decker", "black-and-decker", sortOrder = 4, description = "Reliable power tools for professionals & home use"),
            BrandEntity("br_max", "Max", "max", sortOrder = 5, description = "High-performance Max brand power tools"),
            BrandEntity("br_makita", "Makita", "makita", sortOrder = 6, description = "Japanese heavy-duty industrial machinery"),
            BrandEntity("br_hikoki", "HiKOKI", "hikoki", sortOrder = 7, description = "High performance Hitachi power tools"),
            BrandEntity("br_dongcheng", "Dongcheng", "dongcheng", sortOrder = 8, description = "High reliability professional tools"),
            BrandEntity("br_3m", "3M", "3m", sortOrder = 9, description = "World leader in abrasive grinding & cutting discs"),
            BrandEntity("br_anu", "Anu Tools In-House", "anu-tools", sortOrder = 10, description = "Genuine spare parts & precision consumable discs")
        )
        database.brandDao().insertAll(brands)

        // 5. Products
        val products = listOf(
            ProductEntity(
                productId = "prod_01",
                name = "Bosch GWS 600 Professional Angle Grinder 100mm",
                slug = "bosch-gws-600-angle-grinder",
                sku = "BOSCH-GWS600",
                brandId = "br_bosch",
                brandName = "Bosch",
                categoryId = "cat_grinder",
                categoryName = "Grinder Machine",
                shortDescription = "Compact & reliable 670W angle grinder for metal fabrication.",
                description = "Handy and lightweight angle grinder with 670W champion motor, burst proof guard and two-motion side switch for operator safety.",
                specificationsJson = """{"Power":"670W","Disc Size":"100mm (4 inch)","No-Load Speed":"11000 RPM","Spindle Thread":"M10","Weight":"1.8 kg","Warranty":"6 Months"}""",
                mrp = 3400.0,
                sellingPrice = 2750.0,
                retailerPrice = 2450.0,
                discount = 10.9,
                gstPercentage = 18.0,
                stockQuantity = 26,
                minimumStockLevel = 6,
                unit = "Piece",
                stockStatus = StockStatus.IN_STOCK,
                featured = true
            ),
            ProductEntity(
                productId = "prod_02",
                name = "Makita 2414NB Portable Cut-Off Machine 355mm (14\")",
                slug = "makita-2414nb-cutoff-machine",
                sku = "MAKITA-2414NB",
                brandId = "br_makita",
                brandName = "Makita",
                categoryId = "cat_cutting",
                categoryName = "Cutting Machine",
                shortDescription = "Heavy duty 2000W metal chop saw with spark diversion guard.",
                description = "Rugged aluminum base and D-shape handle for effortless steel bar and pipe cutting. Built-in shaft lock for fast abrasive wheel changes.",
                specificationsJson = """{"Power":"2000W","Wheel Diameter":"355mm (14 inch)","No-Load Speed":"3800 RPM","Arbor":"25.4mm","Weight":"16.2 kg"}""",
                mrp = 14500.0,
                sellingPrice = 11800.0,
                retailerPrice = 10600.0,
                discount = 10.1,
                gstPercentage = 18.0,
                stockQuantity = 8,
                minimumStockLevel = 3,
                unit = "Piece",
                stockStatus = StockStatus.IN_STOCK,
                featured = true
            ),
            ProductEntity(
                productId = "prod_03",
                name = "Dewalt DWD024 13mm Impact Drill Machine",
                slug = "dewalt-dwd024-impact-drill",
                sku = "DEWALT-DWD024",
                brandId = "br_dewalt",
                brandName = "Dewalt",
                categoryId = "cat_drill",
                categoryName = "Drill Machine",
                shortDescription = "650W variable speed hammer drill for masonry and steel.",
                description = "Ergonomic rubberized grip with lock-on switch, high impact per minute rate and depth gauge for accurate hole drilling.",
                specificationsJson = """{"Chuck Capacity":"13mm","Power":"650W","Blows Per Min":"47600 BPM","Max Torque":"8.6 Nm","Weight":"1.82 kg"}""",
                mrp = 4800.0,
                sellingPrice = 3850.0,
                retailerPrice = 3400.0,
                discount = 11.6,
                gstPercentage = 18.0,
                stockQuantity = 19,
                minimumStockLevel = 6,
                unit = "Piece",
                stockStatus = StockStatus.IN_STOCK,
                featured = true
            ),
            ProductEntity(
                productId = "prod_04",
                name = "Dongcheng DZE02-110 Marble Cutter 1240W",
                slug = "dongcheng-dze02-110-marble-cutter",
                sku = "DC-DZE02-110",
                brandId = "br_dongcheng",
                brandName = "Dongcheng",
                categoryId = "cat_cutting",
                categoryName = "Cutting Machine",
                shortDescription = "110mm high speed cutter for tile, granite and marble.",
                description = "High torque 1240W motor capable of sustained wet and dry stone cutting. Solid steel base plate with depth adjust.",
                specificationsJson = """{"Rated Power":"1240W","Blade Diameter":"110mm","No-Load Speed":"13000 RPM","Max Cutting Depth":"32mm"}""",
                mrp = 3200.0,
                sellingPrice = 2450.0,
                retailerPrice = 2150.0,
                discount = 12.2,
                gstPercentage = 18.0,
                stockQuantity = 15,
                minimumStockLevel = 5,
                unit = "Piece",
                stockStatus = StockStatus.IN_STOCK
            ),
            ProductEntity(
                productId = "prod_05",
                name = "HiKOKI CC14ST 355mm Heavy Cut-Off Chop Saw",
                slug = "hikoki-cc14st-cutoff-saw",
                sku = "HIKOKI-CC14ST",
                brandId = "br_hikoki",
                brandName = "HiKOKI",
                categoryId = "cat_cutting",
                categoryName = "Cutting Machine",
                shortDescription = "2000W professional metal fabrication chop saw.",
                description = "Quick clamping vise, spindle lock and enlarged spark guard. Ideal for structural fabrication shops.",
                specificationsJson = """{"Power":"2000W","Blade Size":"355mm","Speed":"3800 RPM","Weight":"17.0 kg"}""",
                mrp = 13900.0,
                sellingPrice = 11200.0,
                retailerPrice = 9950.0,
                discount = 11.1,
                gstPercentage = 18.0,
                stockQuantity = 3,
                minimumStockLevel = 5,
                unit = "Piece",
                stockStatus = StockStatus.LOW_STOCK,
                featured = false
            ),
            ProductEntity(
                productId = "prod_06",
                name = "Bosch GBH 2-26 DRE Rotary Hammer SDS-Plus",
                slug = "bosch-gbh-2-26-dre-rotary-hammer",
                sku = "BOSCH-GBH2-26",
                brandId = "br_bosch",
                brandName = "Bosch",
                categoryId = "cat_drill",
                categoryName = "Drill Machine",
                shortDescription = "800W multi-mode rotary hammer for heavy concrete coring.",
                description = "Fastest drilling rate in its class with rotating brush plate for equal power in forward and reverse rotation.",
                specificationsJson = """{"Power":"800W","Impact Energy":"2.7 Joules","Drilling Dia in Concrete":"4-26mm","Modes":"Drill, Hammer, Chisel"}""",
                mrp = 12500.0,
                sellingPrice = 9800.0,
                retailerPrice = 8900.0,
                discount = 9.1,
                gstPercentage = 18.0,
                stockQuantity = 7,
                minimumStockLevel = 4,
                unit = "Piece",
                stockStatus = StockStatus.IN_STOCK
            ),
            ProductEntity(
                productId = "prod_07",
                name = "3M Green Corps 4\" Heavy Grinding Disc (Pack of 25)",
                slug = "3m-4inch-grinding-disc-pack25",
                sku = "3M-GD-100-25",
                brandId = "br_3m",
                brandName = "3M",
                categoryId = "cat_cutting_discs",
                categoryName = "Cutting & Grinding Discs",
                shortDescription = "Ceramic grain abrasive grinding wheels for weld seam leveling.",
                description = "Long lasting cut with minimal vibration. Designed for heavy carbon steel and stainless steel grinding.",
                specificationsJson = """{"Diameter":"100mm (4 inch)","Thickness":"6mm","Bore":"16mm","Pack Size":"25 Discs"}""",
                mrp = 1875.0,
                sellingPrice = 1450.0,
                retailerPrice = 1250.0,
                discount = 13.7,
                gstPercentage = 18.0,
                stockQuantity = 2,
                minimumStockLevel = 8,
                unit = "Pack",
                stockStatus = StockStatus.LOW_STOCK
            ),
            ProductEntity(
                productId = "prod_08",
                name = "Anu Ultra-Thin 4\" Metal Cutting Wheel (Box of 50)",
                slug = "anu-ultra-thin-4inch-cutting-wheel",
                sku = "ANU-CW-105-50",
                brandId = "br_anu",
                brandName = "Anu Tools In-House",
                categoryId = "cat_cutting_discs",
                categoryName = "Cutting & Grinding Discs",
                shortDescription = "1.0mm ultra-fast burr-free steel cutting discs.",
                description = "Reinforced double fiberglass mesh for maximum burst resistance and clean razor-sharp cutting.",
                specificationsJson = """{"Diameter":"105mm","Thickness":"1.0mm","Bore":"16mm","Quantity":"50 Discs/Box","Max RPM":"15300"}""",
                mrp = 1500.0,
                sellingPrice = 1100.0,
                retailerPrice = 950.0,
                discount = 13.6,
                gstPercentage = 18.0,
                stockQuantity = 45,
                minimumStockLevel = 15,
                unit = "Box",
                stockStatus = StockStatus.IN_STOCK,
                featured = true
            ),
            ProductEntity(
                productId = "prod_09",
                name = "Copper Armature for Bosch GWS 600 / 6-100",
                slug = "armature-bosch-gws-600",
                sku = "SP-BOSCH-GWS600-ARM",
                brandId = "br_bosch",
                brandName = "Bosch",
                categoryId = "cat_spare_parts",
                categoryName = "Spare Parts",
                shortDescription = "OEM replacement rotor armature with balanced fan.",
                description = "Pure copper magnet wire winding with epoxy coated insulation against abrasive dust.",
                specificationsJson = """{"Winding":"100% Pure Copper","Shaft Length":"152mm","Compatibility":"GWS 600, GWS 6-100"}""",
                mrp = 850.0,
                sellingPrice = 620.0,
                retailerPrice = 520.0,
                discount = 16.1,
                gstPercentage = 18.0,
                stockQuantity = 0,
                minimumStockLevel = 6,
                unit = "Piece",
                stockStatus = StockStatus.OUT_OF_STOCK
            ),
            ProductEntity(
                productId = "prod_10",
                name = "High-Grade Carbon Brush Set (10 Pairs Pack)",
                slug = "carbon-brush-pack-10-pairs",
                sku = "SP-CB-10P",
                brandId = "br_anu",
                brandName = "Anu Tools In-House",
                categoryId = "cat_spare_parts",
                categoryName = "Spare Parts",
                shortDescription = "Universal copper lead graphite brushes for grinders & drills.",
                description = "Low-sparking high conductivity carbon brushes with brass terminal and copper spring.",
                specificationsJson = """{"Size":"5 x 8 x 12 mm","Quantity":"10 Pairs (20 Pieces)","Application":"4\" Grinders & Marble Cutters"}""",
                mrp = 450.0,
                sellingPrice = 320.0,
                retailerPrice = 260.0,
                discount = 18.7,
                gstPercentage = 18.0,
                stockQuantity = 38,
                minimumStockLevel = 10,
                unit = "Pack",
                stockStatus = StockStatus.IN_STOCK
            )
        )
        database.productDao().insertAll(products)

        // 6. Retailers & Orders:
        // Loaded LIVE directly from real Firestore collections "retailers" and "orders"
        // in project anu-tools-production. No mock data is seeded.
        database.retailerDao().deleteAll()
        database.orderDao().deleteLegacyMockOrders()

        val now = System.currentTimeMillis()
        val oneHourAgo = now - 3600000L
        val threeHoursAgo = now - 10800000L
        val yesterday = now - 86400000L

        // 8. Inventory Transactions
        val inventoryLogs = listOf(
            InventoryTransactionEntity(
                transactionId = "tx_01",
                productId = "prod_01",
                productName = "Bosch GWS 600 Professional Angle Grinder",
                sku = "BOSCH-GWS600",
                type = TransactionType.STOCK_ADDED,
                quantity = 30,
                previousStock = 0,
                newStock = 30,
                reason = "Inward Shipment from Bosch India Distributor",
                createdBy = "admin@anutools.com",
                createdAt = yesterday
            ),
            InventoryTransactionEntity(
                transactionId = "tx_02",
                productId = "prod_02",
                productName = "Makita 2414NB Portable Cut-Off Machine",
                sku = "MAKITA-2414NB",
                type = TransactionType.ORDER_CONFIRMED,
                quantity = -2,
                previousStock = 10,
                newStock = 8,
                reason = "Stock deducted for Order ANU-2026-000122",
                orderId = "ord_002",
                createdBy = "admin@anutools.com",
                createdAt = threeHoursAgo + 1800000L
            ),
            InventoryTransactionEntity(
                transactionId = "tx_03",
                productId = "prod_07",
                productName = "3M Green Corps 4\" Heavy Grinding Disc (Pack of 25)",
                sku = "3M-GD-100-25",
                type = TransactionType.MANUAL_ADJUSTMENT,
                quantity = -6,
                previousStock = 8,
                newStock = 2,
                reason = "Physical warehouse count sync - 6 packs damaged in transit",
                createdBy = "staff@anutools.com",
                createdAt = yesterday - 10000000L
            )
        )
        database.inventoryTransactionDao().insertAll(inventoryLogs)

        // 9. Notifications: Real push notifications from Firebase Cloud Messaging (FCM)
        // No fake notifications inserted.

        // 10. Audit Logs
        val auditLogs = listOf(
            AuditLogEntity(
                logId = "log_01",
                action = "SYSTEM_INITIALIZED",
                userId = "usr_super_admin",
                userName = "Sunil Kumar",
                entityType = "SYSTEM",
                entityId = "ANU_TOOLS_SYS",
                details = "Anu Tools Admin business management platform initialized.",
                timestamp = yesterday - 86400000L
            ),
            AuditLogEntity(
                logId = "log_02",
                action = "ORDER_CONFIRMED",
                userId = "usr_admin",
                userName = "Anurag Sharma",
                entityType = "ORDER",
                entityId = "ANU-2026-000122",
                oldValue = "PENDING",
                newValue = "CONFIRMED",
                details = "Order confirmed. Inventory automatically deducted.",
                timestamp = threeHoursAgo + 1800000L
            )
        )
        database.auditLogDao().insertAll(auditLogs)
    }
}
