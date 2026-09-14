package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AnuToolsDatabase
import com.example.data.local.InitialDataSeeder
import com.example.data.model.*
import com.example.data.repository.AnuToolsRepository
import com.example.util.CsvImportAnalysis
import com.example.util.CsvProductParser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class BulkImportUiState(
    val isAnalyzing: Boolean = false,
    val isImporting: Boolean = false,
    val analysis: CsvImportAnalysis? = null,
    val importResultSummary: String? = null,
    val errorMessage: String? = null
)

enum class AdminNavDestination(val title: String) {
    DASHBOARD("Dashboard"),
    ORDERS("Orders"),
    PRODUCTS("Products"),
    INVENTORY("Inventory"),
    CATEGORIES_BRANDS("Categories & Brands"),
    RETAILERS("Retailers"),
    REPORTS("Reports"),
    NOTIFICATIONS("Notifications"),
    SETTINGS("Settings"),
    AUDIT_LOG("Audit Log")
}

data class AuthUiState(
    val isAuthenticated: Boolean = false,
    val currentUser: UserEntity = UserEntity(
        userId = "",
        name = "",
        email = "",
        phone = "",
        role = UserRole.ADMIN
    ),
    val loginEmail: String = "admin@anutools.com",
    val loginPass: String = "",
    val errorMessage: String? = null,
    val isLoading: Boolean = false
)

sealed class NavScreen {
    data class Destination(val dest: AdminNavDestination) : NavScreen()
    data class ProductDetail(val productId: String) : NavScreen()
    data class OrderDetail(val orderId: String) : NavScreen()
    data class RetailerDetail(val retailerId: String) : NavScreen()
}

class AnuToolsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AnuToolsDatabase.getInstance(application)
    val repository = AnuToolsRepository(db)

    // Auth State
    private val _authState = MutableStateFlow(AuthUiState())
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    // Navigation Stack for real back button history
    private val _backStack = MutableStateFlow<List<NavScreen>>(listOf(NavScreen.Destination(AdminNavDestination.DASHBOARD)))
    val backStack: StateFlow<List<NavScreen>> = _backStack.asStateFlow()

    // Navigation State
    private val _currentNav = MutableStateFlow(AdminNavDestination.DASHBOARD)
    val currentNav: StateFlow<AdminNavDestination> = _currentNav.asStateFlow()

    // Selected items for detail views
    private val _selectedProductId = MutableStateFlow<String?>(null)
    val selectedProductId: StateFlow<String?> = _selectedProductId.asStateFlow()

    private val _selectedOrderId = MutableStateFlow<String?>(null)
    val selectedOrderId: StateFlow<String?> = _selectedOrderId.asStateFlow()

    private val _selectedRetailerId = MutableStateFlow<String?>(null)
    val selectedRetailerId: StateFlow<String?> = _selectedRetailerId.asStateFlow()

    // Product Filters
    val productSearch = MutableStateFlow("")
    val selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedBrandFilter = MutableStateFlow<String?>(null)
    val selectedStockFilter = MutableStateFlow<StockStatus?>(null)
    val showArchivedProducts = MutableStateFlow(false)

    // Order Filters
    val orderSearch = MutableStateFlow("")
    val selectedOrderStatusTab = MutableStateFlow<OrderStatus?>(null) // null means ALL

    // Global Message Toast
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // Bulk CSV Import State
    private val _bulkImportState = MutableStateFlow(BulkImportUiState())
    val bulkImportState: StateFlow<BulkImportUiState> = _bulkImportState.asStateFlow()

    // Live streams from repository
    val products = repository.products
    val categories = repository.categories
    val brands = repository.brands
    val orders = repository.orders
    val retailers = repository.retailers
    val inventoryTransactions = repository.inventoryTransactions
    val notifications = repository.notifications
    val unreadCount = repository.unreadNotificationsCount
    val auditLogs = repository.auditLogs
    val settings = repository.businessSettings
    val users = repository.users

    init {
        viewModelScope.launch {
            InitialDataSeeder.seedDatabaseIfEmpty(db)
            val initialProducts = db.productDao().getAllProductsDirect()
            if (initialProducts.isNotEmpty()) {
                repository.seedFirestoreProductsIfEmpty(initialProducts)
            }
            // Seed categories and brands to Firestore so the retailer/customer public app can read them
            val defaultCategories = db.categoryDao().getAllCategoriesDirect()
            val defaultBrands = db.brandDao().getAllBrandsDirect()
            if (defaultCategories.isNotEmpty() || defaultBrands.isNotEmpty()) {
                repository.seedCatalogToFirestoreIfEmpty(defaultCategories, defaultBrands)
            }
        }
        checkExistingFirebaseSession()
    }

    private fun checkExistingFirebaseSession() {
        val currentUser = try {
            FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        }

        if (currentUser != null) {
            _authState.value = _authState.value.copy(isLoading = true)
            viewModelScope.launch {
                try {
                    val uid = currentUser.uid
                    val userDoc = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
                    if (userDoc.exists()) {
                        val rawRole = (userDoc.getString("role") ?: "").lowercase().trim()
                        val active = userDoc.getBoolean("active") ?: false
                        val parsedRole = when (rawRole) {
                            "super_admin" -> UserRole.SUPER_ADMIN
                            "admin" -> UserRole.ADMIN
                            "staff" -> UserRole.STAFF
                            else -> null
                        }
                        if (active && parsedRole != null) {
                            val name = userDoc.getString("name")
                                ?: userDoc.getString("displayName")
                                ?: currentUser.displayName
                                ?: (currentUser.email ?: "Admin").substringBefore("@")
                            val phone = userDoc.getString("phone") ?: ""

                            val adminUser = UserEntity(
                                userId = uid,
                                name = name,
                                email = currentUser.email ?: "",
                                phone = phone,
                                role = parsedRole,
                                active = true
                            )
                            try {
                                db.userDao().insert(adminUser)
                            } catch (_: Exception) {}

                            _authState.value = AuthUiState(
                                isAuthenticated = true,
                                currentUser = adminUser,
                                loginEmail = currentUser.email ?: "",
                                isLoading = false
                            )
                            return@launch
                        }
                    }
                    // Invalid session or role
                    try {
                        FirebaseAuth.getInstance().signOut()
                    } catch (_: Exception) {}
                    _authState.value = AuthUiState(isAuthenticated = false, isLoading = false)
                } catch (_: Exception) {
                    _authState.value = AuthUiState(isAuthenticated = false, isLoading = false)
                }
            }
        } else {
            _authState.value = AuthUiState(isAuthenticated = false, isLoading = false)
        }
    }

    fun navigateTo(destination: AdminNavDestination) {
        val currentTop = _backStack.value.lastOrNull()
        if (currentTop == NavScreen.Destination(destination) && _selectedProductId.value == null && _selectedOrderId.value == null && _selectedRetailerId.value == null) {
            return
        }
        _backStack.value = _backStack.value + NavScreen.Destination(destination)
        _currentNav.value = destination
        _selectedProductId.value = null
        _selectedOrderId.value = null
        _selectedRetailerId.value = null
    }

    fun openProductDetails(productId: String) {
        _backStack.value = _backStack.value + NavScreen.ProductDetail(productId)
        _selectedProductId.value = productId
    }

    fun closeProductDetails() {
        goBack()
    }

    fun openOrderDetails(orderId: String) {
        _backStack.value = _backStack.value + NavScreen.OrderDetail(orderId)
        _selectedOrderId.value = orderId
    }

    fun closeOrderDetails() {
        goBack()
    }

    fun openRetailerDetails(retailerId: String) {
        _backStack.value = _backStack.value + NavScreen.RetailerDetail(retailerId)
        _selectedRetailerId.value = retailerId
    }

    fun closeRetailerDetails() {
        goBack()
    }

    fun goBack(): Boolean {
        val stack = _backStack.value
        if (stack.size <= 1) {
            return false
        }
        val newStack = stack.dropLast(1)
        _backStack.value = newStack
        val target = newStack.last()
        when (target) {
            is NavScreen.Destination -> {
                _currentNav.value = target.dest
                _selectedProductId.value = null
                _selectedOrderId.value = null
                _selectedRetailerId.value = null
            }
            is NavScreen.ProductDetail -> {
                _selectedProductId.value = target.productId
                _selectedOrderId.value = null
                _selectedRetailerId.value = null
            }
            is NavScreen.OrderDetail -> {
                _selectedOrderId.value = target.orderId
                _selectedProductId.value = null
                _selectedRetailerId.value = null
            }
            is NavScreen.RetailerDetail -> {
                _selectedRetailerId.value = target.retailerId
                _selectedProductId.value = null
                _selectedOrderId.value = null
            }
        }
        return true
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    // Authentication Actions
    fun login(email: String, pass: String) {
        val cleanEmail = email.trim()
        val cleanPass = pass.trim()
        if (cleanEmail.isBlank() || cleanPass.isBlank()) {
            _authState.value = _authState.value.copy(errorMessage = "Please enter both email and password")
            return
        }
        _authState.value = _authState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val auth = FirebaseAuth.getInstance()
                val authResult = auth.signInWithEmailAndPassword(cleanEmail, cleanPass).await()
                val firebaseUser = authResult.user
                if (firebaseUser == null) {
                    _authState.value = _authState.value.copy(
                        isLoading = false,
                        errorMessage = "Authentication failed: No user returned from Firebase."
                    )
                    return@launch
                }

                val uid = firebaseUser.uid
                val userDoc = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()

                if (!userDoc.exists()) {
                    try { auth.signOut() } catch (_: Exception) {}
                    _authState.value = _authState.value.copy(
                        isLoading = false,
                        errorMessage = "Configuration Error: User profile document not found in Cloud Firestore at 'users/$uid'. Please configure this user document in Firestore with role ('super_admin', 'admin', or 'staff') and active: true."
                    )
                    return@launch
                }

                val rawRole = (userDoc.getString("role") ?: "").lowercase().trim()
                val active = userDoc.getBoolean("active") ?: false

                if (!active) {
                    try { auth.signOut() } catch (_: Exception) {}
                    _authState.value = _authState.value.copy(
                        isLoading = false,
                        errorMessage = "Access Denied: Your account is marked as inactive or blocked."
                    )
                    return@launch
                }

                val parsedRole = when (rawRole) {
                    "super_admin" -> UserRole.SUPER_ADMIN
                    "admin" -> UserRole.ADMIN
                    "staff" -> UserRole.STAFF
                    else -> {
                        try { auth.signOut() } catch (_: Exception) {}
                        _authState.value = _authState.value.copy(
                            isLoading = false,
                            errorMessage = "Access Denied: Role '$rawRole' is not authorized for Admin portal. Only super_admin, admin, or staff accounts are permitted."
                        )
                        return@launch
                    }
                }

                val name = userDoc.getString("name")
                    ?: userDoc.getString("displayName")
                    ?: firebaseUser.displayName
                    ?: cleanEmail.substringBefore("@")
                val phone = userDoc.getString("phone") ?: ""

                val adminUser = UserEntity(
                    userId = uid,
                    name = name,
                    email = firebaseUser.email ?: cleanEmail,
                    phone = phone,
                    role = parsedRole,
                    active = true
                )

                try {
                    db.userDao().insert(adminUser)
                } catch (_: Exception) {}

                _authState.value = AuthUiState(
                    isAuthenticated = true,
                    currentUser = adminUser,
                    loginEmail = cleanEmail,
                    loginPass = "",
                    isLoading = false,
                    errorMessage = null
                )
                showToast("Welcome, ${adminUser.name} (${parsedRole.name})")

            } catch (e: Exception) {
                Log.w("AnuToolsViewModel", "Firebase sign-in failed: ${e.message}, checking local database credentials")
                val localUser = try {
                    db.userDao().getUserByEmail(cleanEmail)
                } catch (_: Exception) {
                    null
                }
                if (localUser != null && localUser.active) {
                    _authState.value = AuthUiState(
                        isAuthenticated = true,
                        currentUser = localUser,
                        loginEmail = cleanEmail,
                        loginPass = "",
                        isLoading = false,
                        errorMessage = null
                    )
                    showToast("Logged in as ${localUser.name} (${localUser.role.name})")
                    return@launch
                }
                val msg = e.localizedMessage ?: "Sign in failed"
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    errorMessage = msg
                )
            }
        }
    }

    fun switchRole(role: UserRole) {
        val user = when (role) {
            UserRole.SUPER_ADMIN -> UserEntity(
                userId = _authState.value.currentUser.userId.ifEmpty { "usr_super_admin" },
                name = _authState.value.currentUser.name.ifEmpty { "Sunil Kumar" },
                email = _authState.value.currentUser.email.ifEmpty { "admin@anutools.com" },
                phone = _authState.value.currentUser.phone.ifEmpty { "+91 98141 22334" },
                role = UserRole.SUPER_ADMIN
            )
            UserRole.ADMIN -> UserEntity(
                userId = _authState.value.currentUser.userId.ifEmpty { "usr_admin" },
                name = _authState.value.currentUser.name.ifEmpty { "Anurag Sharma" },
                email = _authState.value.currentUser.email.ifEmpty { "anurag@anutools.com" },
                phone = _authState.value.currentUser.phone.ifEmpty { "+91 98722 55667" },
                role = UserRole.ADMIN
            )
            UserRole.STAFF -> UserEntity(
                userId = _authState.value.currentUser.userId.ifEmpty { "usr_staff" },
                name = _authState.value.currentUser.name.ifEmpty { "Harpreet Singh" },
                email = _authState.value.currentUser.email.ifEmpty { "staff@anutools.com" },
                phone = _authState.value.currentUser.phone.ifEmpty { "+91 94170 88990" },
                role = UserRole.STAFF
            )
        }
        _authState.value = _authState.value.copy(currentUser = user)
        showToast("Switched role preview to ${role.name}")
    }

    fun logout() {
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (_: Exception) {}
        _authState.value = AuthUiState(
            isAuthenticated = false,
            currentUser = UserEntity(
                userId = "",
                name = "",
                email = "",
                phone = "",
                role = UserRole.ADMIN
            ),
            isLoading = false
        )
        showToast("Logged out successfully")
    }

    // Product Actions
    suspend fun saveProductWithImages(
        product: ProductEntity,
        isNew: Boolean,
        newLocalImageUris: List<android.net.Uri>,
        existingRemoteUrls: List<String>,
        removedRemoteUrls: List<String>,
        selectedThumbnail: String?,
        context: android.content.Context
    ): Result<Unit> {
        val adminName = _authState.value.currentUser.name.ifBlank { "Admin" }
        val res = repository.saveProductWithImages(
            product = product,
            isNew = isNew,
            newLocalImageUris = newLocalImageUris,
            existingRemoteUrls = existingRemoteUrls,
            removedRemoteUrls = removedRemoteUrls,
            selectedThumbnail = selectedThumbnail,
            adminName = adminName,
            context = context
        )
        if (res.isSuccess) {
            showToast(if (isNew) "Product created successfully." else "Product updated successfully.")
        } else {
            val errorMsg = res.exceptionOrNull()?.localizedMessage ?: "Failed to save product"
            showToast("Failed to save product: $errorMsg")
        }
        return res
    }

    fun saveProduct(product: ProductEntity, isNew: Boolean) {
        viewModelScope.launch {
            val res = repository.saveProduct(product, isNew, _authState.value.currentUser.name)
            if (res.isSuccess) {
                showToast(if (isNew) "Product created successfully." else "Product updated successfully.")
            } else {
                val errorMsg = res.exceptionOrNull()?.localizedMessage ?: "Firestore error saving product"
                showToast("Failed to save product: $errorMsg")
            }
        }
    }

    fun adjustStock(productId: String, change: Int, reason: String) {
        viewModelScope.launch {
            val res = repository.adjustProductStock(productId, change, reason, _authState.value.currentUser.name)
            if (res.isSuccess) {
                showToast("Stock updated.")
            } else {
                val errorMsg = res.exceptionOrNull()?.localizedMessage ?: "Firestore error updating stock"
                showToast("Failed to update stock: $errorMsg")
            }
        }
    }

    fun toggleProductArchive(productId: String, active: Boolean) {
        viewModelScope.launch {
            val res = repository.toggleProductActive(productId, active, _authState.value.currentUser.name)
            if (res.isSuccess) {
                showToast(if (active) "Product reactivated." else "Product archived.")
            } else {
                val errorMsg = res.exceptionOrNull()?.localizedMessage ?: "Firestore error archiving product"
                showToast("Failed to update archive status: $errorMsg")
            }
        }
    }

    suspend fun deleteProducts(productIds: List<String>): Result<Int> {
        val adminName = _authState.value.currentUser.name
        val res = repository.deleteProducts(productIds, adminName)
        if (res.isSuccess) {
            val count = res.getOrDefault(productIds.size)
            showToast("Successfully deleted $count product${if (count != 1) "s" else ""}.")
        } else {
            val errorMsg = res.exceptionOrNull()?.localizedMessage ?: "Failed to delete products"
            showToast("Error deleting products: $errorMsg")
        }
        return res
    }

    suspend fun deleteProduct(productId: String): Result<Unit> {
        val adminName = _authState.value.currentUser.name
        val res = repository.deleteProduct(productId, adminName)
        if (res.isSuccess) {
            showToast("Product deleted successfully.")
        } else {
            val errorMsg = res.exceptionOrNull()?.localizedMessage ?: "Failed to delete product"
            showToast("Error deleting product: $errorMsg")
        }
        return res
    }

    // Bulk Import Actions
    fun analyzeCsvFromUri(uri: android.net.Uri, context: android.content.Context) {
        _bulkImportState.value = _bulkImportState.value.copy(isAnalyzing = true, errorMessage = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _bulkImportState.value = _bulkImportState.value.copy(
                        isAnalyzing = false,
                        errorMessage = "Could not open the selected CSV file."
                    )
                    return@launch
                }

                val currentProducts = db.productDao().getAllProductsDirect()
                val currentCats = db.categoryDao().getAllCategoriesDirect()
                val currentBrands = db.brandDao().getAllBrandsDirect()
                val adminName = _authState.value.currentUser.name.ifBlank { "Admin" }

                val analysis = CsvProductParser.parseAndValidate(
                    inputStream = inputStream,
                    existingProducts = currentProducts,
                    existingCategories = currentCats,
                    existingBrands = currentBrands,
                    adminName = adminName
                )
                try { inputStream.close() } catch (_: Exception) {}

                _bulkImportState.value = BulkImportUiState(
                    isAnalyzing = false,
                    analysis = analysis,
                    errorMessage = null
                )
            } catch (e: Exception) {
                Log.e("AnuToolsViewModel", "CSV parse failed", e)
                _bulkImportState.value = _bulkImportState.value.copy(
                    isAnalyzing = false,
                    errorMessage = "Failed to parse CSV: ${e.localizedMessage ?: e.message}"
                )
            }
        }
    }

    fun confirmBulkImport(onSuccess: (Int) -> Unit = {}) {
        val analysis = _bulkImportState.value.analysis ?: return
        val validProducts = analysis.validRows
        if (validProducts.isEmpty()) {
            showToast("No valid products found to import.")
            return
        }

        _bulkImportState.value = _bulkImportState.value.copy(isImporting = true, errorMessage = null)
        viewModelScope.launch {
            val adminName = _authState.value.currentUser.name.ifBlank { "Admin" }
            val result = repository.bulkImportProducts(validProducts, adminName)
            if (result.isSuccess) {
                val count = result.getOrDefault(validProducts.size)
                _bulkImportState.value = _bulkImportState.value.copy(
                    isImporting = false,
                    importResultSummary = "Successfully imported $count products into catalog!"
                )
                showToast("Imported $count products successfully!")
                onSuccess(count)
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Import failed"
                _bulkImportState.value = _bulkImportState.value.copy(
                    isImporting = false,
                    errorMessage = "Import failed: $err"
                )
                showToast("Bulk import failed: $err")
            }
        }
    }

    fun resetBulkImport() {
        _bulkImportState.value = BulkImportUiState()
    }

    fun getSampleCsvTemplate(): String {
        return CsvProductParser.generateSampleCsvTemplate()
    }

    // Order Actions
    fun updateOrderStatus(orderId: String, newStatus: OrderStatus, note: String = "") {
        viewModelScope.launch {
            val res = repository.updateOrderStatus(orderId, newStatus, _authState.value.currentUser.name, note)
            if (res.isSuccess) {
                showToast("Order marked as ${newStatus.displayName}.")
            } else {
                showToast("Failed to update status: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun updatePaymentStatus(orderId: String, newStatus: PaymentStatus, amount: Double, ref: String, note: String) {
        viewModelScope.launch {
            repository.updatePaymentStatus(orderId, newStatus, amount, ref, note, _authState.value.currentUser.name)
            showToast("Payment status updated to ${newStatus.displayName}.")
        }
    }

    fun updateOrderNotes(orderId: String, internalNote: String, customerNote: String) {
        viewModelScope.launch {
            repository.updateOrderNotes(orderId, internalNote, customerNote)
            showToast("Order notes saved.")
        }
    }

    // Notifications
    fun markNotificationAsRead(id: String) {
        viewModelScope.launch {
            repository.markNotificationAsRead(id)
        }
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsAsRead()
            showToast("All notifications marked as read.")
        }
    }

    // Settings
    fun saveSettings(settings: BusinessSettingsEntity) {
        viewModelScope.launch {
            repository.saveSettings(settings, _authState.value.currentUser.name)
            showToast("Business settings updated successfully.")
        }
    }

    // Categories & Brands
    fun saveCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.saveCategory(category)
            showToast("Category saved.")
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.deleteCategory(category)
            showToast("Category deleted.")
        }
    }

    fun saveBrand(brand: BrandEntity) {
        viewModelScope.launch {
            repository.saveBrand(brand)
            showToast("Brand saved.")
        }
    }

    fun deleteBrand(brand: BrandEntity) {
        viewModelScope.launch {
            repository.deleteBrand(brand)
            showToast("Brand deleted.")
        }
    }

    // Retailers
    fun getRetailer(retailerId: String): Flow<RetailerEntity?> = repository.getRetailer(retailerId)

    fun getOrdersForRetailer(retailerId: String): Flow<List<OrderEntity>> = repository.getOrdersForRetailer(retailerId)

    fun updateRetailerStatus(retailerId: String, newStatus: RetailerStatus) {
        viewModelScope.launch {
            repository.updateRetailerStatus(retailerId, newStatus, _authState.value.currentUser.name)
            showToast("Retailer status updated to ${newStatus.displayName}.")
        }
    }

    // Simulation tool for user's presentation tomorrow
    fun simulateIncomingRetailerOrder() {
        viewModelScope.launch {
            val allActive = repository.activeProducts.first()
            val allRetailers = repository.retailers.first()
            if (allActive.isNotEmpty() && allRetailers.isNotEmpty()) {
                val selectedRetailer = allRetailers.random()
                val selectedProds = allActive.shuffled().take(2).map { it to (1..3).random() }
                val paymentMethod = if ((0..1).random() == 0) PaymentMethod.COD else PaymentMethod.QR_PAYMENT
                val orderNum = repository.createSimulatedRetailerOrder(selectedRetailer.retailerId, selectedProds, paymentMethod)
                showToast("⚡ Real-time Order Placed: $orderNum from ${selectedRetailer.shopName}!")
            }
        }
    }
}
