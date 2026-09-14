package com.example.ui.screens

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.CategoryEntity
import com.example.data.model.ProductEntity
import com.example.data.model.StockStatus
import com.example.ui.AnuToolsViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.ImageModelResolver
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    viewModel: AnuToolsViewModel,
    onOpenProductDetail: (String) -> Unit
) {
    val allProducts by viewModel.products.collectAsState(initial = emptyList())
    val categories by viewModel.categories.collectAsState(initial = emptyList())
    val brands by viewModel.brands.collectAsState(initial = emptyList())

    val searchQuery by viewModel.productSearch.collectAsState()
    val categoryFilter by viewModel.selectedCategoryFilter.collectAsState()
    val brandFilter by viewModel.selectedBrandFilter.collectAsState()
    val stockFilter by viewModel.selectedStockFilter.collectAsState()
    val showArchived by viewModel.showArchivedProducts.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedProductIds = remember { mutableStateListOf<String>() }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isDeletingProducts by remember { mutableStateOf(false) }
    var singleProductToDelete by remember { mutableStateOf<ProductEntity?>(null) }

    var showAddProductDialog by remember { mutableStateOf(false) }
    var showBulkImportDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var quickStockAdjustProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var stockDeltaText by remember { mutableStateOf("5") }
    var stockReasonText by remember { mutableStateOf("Warehouse adjustment") }

    val filteredProducts = remember(allProducts, searchQuery, categoryFilter, brandFilter, stockFilter, showArchived) {
        allProducts.filter { product ->
            val matchesActive = if (showArchived) !product.active else product.active
            val matchesSearch = searchQuery.isBlank() ||
                    product.name.contains(searchQuery, ignoreCase = true) ||
                    product.sku.contains(searchQuery, ignoreCase = true) ||
                    product.brandName.contains(searchQuery, ignoreCase = true) ||
                    product.categoryName.contains(searchQuery, ignoreCase = true)
            val matchesCategory = categoryFilter == null || product.categoryId == categoryFilter
            val matchesBrand = brandFilter == null || product.brandId == brandFilter
            val matchesStock = stockFilter == null || product.stockStatus == stockFilter
            matchesActive && matchesSearch && matchesCategory && matchesBrand && matchesStock
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header & Search
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (isSelectionMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("selection_toolbar")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        isSelectionMode = false
                                        selectedProductIds.clear()
                                    },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("exit_selection_button")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Exit Selection")
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${selectedProductIds.size} Selected",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val allSelected = filteredProducts.isNotEmpty() && selectedProductIds.size == filteredProducts.size
                                OutlinedButton(
                                    onClick = {
                                        if (allSelected) {
                                            selectedProductIds.clear()
                                        } else {
                                            selectedProductIds.clear()
                                            selectedProductIds.addAll(filteredProducts.map { it.productId })
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("select_all_toggle_button")
                                ) {
                                    Icon(
                                        imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (allSelected) "Deselect All" else "Select All (${filteredProducts.size})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (selectedProductIds.isNotEmpty()) {
                                            showDeleteConfirmDialog = true
                                        }
                                    },
                                    enabled = selectedProductIds.isNotEmpty() && !isDeletingProducts,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("delete_selected_button")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete (${selectedProductIds.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Product Catalogue",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Manage inventory and pricing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = { isSelectionMode = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("start_selection_mode_button")
                        ) {
                            Icon(Icons.Default.Checklist, contentDescription = "Select", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Select", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = { showBulkImportDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("import_products_button")
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = "Import CSV", modifier = Modifier.size(16.dp), tint = IndustrialOrange)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import CSV", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = IndustrialOrange)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        FilterChip(
                            selected = showArchived,
                            onClick = { viewModel.showArchivedProducts.value = !showArchived },
                            label = { Text(if (showArchived) "Archived" else "Active", fontSize = 12.sp) },
                            leadingIcon = if (showArchived) {
                                { Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.productSearch.value = it },
                    placeholder = { Text("Search by name, SKU, brand or category...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.productSearch.value = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Chips Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = stockFilter == null,
                            onClick = { viewModel.selectedStockFilter.value = null },
                            label = { Text("All Stock") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = stockFilter == StockStatus.IN_STOCK,
                            onClick = { viewModel.selectedStockFilter.value = if (stockFilter == StockStatus.IN_STOCK) null else StockStatus.IN_STOCK },
                            label = { Text("In Stock") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = stockFilter == StockStatus.LOW_STOCK,
                            onClick = { viewModel.selectedStockFilter.value = if (stockFilter == StockStatus.LOW_STOCK) null else StockStatus.LOW_STOCK },
                            label = { Text("Low Stock") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = stockFilter == StockStatus.OUT_OF_STOCK,
                            onClick = { viewModel.selectedStockFilter.value = if (stockFilter == StockStatus.OUT_OF_STOCK) null else StockStatus.OUT_OF_STOCK },
                            label = { Text("Out of Stock") }
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = categoryFilter == cat.categoryId,
                            onClick = { viewModel.selectedCategoryFilter.value = if (categoryFilter == cat.categoryId) null else cat.categoryId },
                            label = { Text(cat.name) }
                        )
                    }
                }
            }

            HorizontalDivider()

            // Products List
            if (filteredProducts.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Construction,
                    title = "No Products Found",
                    description = if (searchQuery.isNotBlank() || categoryFilter != null) "Try adjusting your filters or search keywords." else "Add your first tool product to build the Anu Tools catalogue.",
                    actionLabel = "Add Product",
                    onAction = { showAddProductDialog = true },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredProducts, key = { it.productId }) { product ->
                        val isSelected = selectedProductIds.contains(product.productId)
                        Card(
                            onClick = {
                                if (isSelectionMode) {
                                    if (isSelected) {
                                        selectedProductIds.remove(product.productId)
                                    } else {
                                        selectedProductIds.add(product.productId)
                                    }
                                } else {
                                    onOpenProductDetail(product.productId)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) IndustrialOrange.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                            ),
                            border = if (isSelected) {
                                BorderStroke(2.dp, IndustrialOrange)
                            } else {
                                CardDefaults.outlinedCardBorder()
                            },
                            modifier = Modifier.testTag("product_card_${product.productId}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isSelectionMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    if (checked) {
                                                        if (!selectedProductIds.contains(product.productId)) {
                                                            selectedProductIds.add(product.productId)
                                                        }
                                                    } else {
                                                        selectedProductIds.remove(product.productId)
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = IndustrialOrange,
                                                    checkmarkColor = Color.White
                                                ),
                                                modifier = Modifier.testTag("product_checkbox_${product.productId}")
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (product.thumbnailUrl.isNotBlank()) {
                                                AsyncImage(
                                                    model = ImageModelResolver.resolveImageModel(product.thumbnailUrl),
                                                    contentDescription = product.name,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Hardware,
                                                    contentDescription = null,
                                                    tint = IndustrialOrange,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = product.name,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${product.brandName} • SKU: ${product.sku} • ${product.categoryName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    StockStatusBadge(
                                        status = product.stockStatus,
                                        quantity = product.stockQuantity,
                                        unit = product.unit
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(modifier = Modifier.height(10.dp))

                                // Pricing & Quick Controls
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Column {
                                            Text(
                                                text = "Retailer Price",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = formatCurrency(product.retailerPrice),
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = IndustrialOrange
                                                )
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "Selling MRP",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = formatCurrency(product.mrp),
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "GST",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${product.gstPercentage.toInt()}%",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    // Action Buttons
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                quickStockAdjustProduct = product
                                                stockDeltaText = "5"
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Inventory2,
                                                contentDescription = "Adjust Stock",
                                                tint = IndustrialOrange
                                            )
                                        }
                                        IconButton(
                                            onClick = { editingProduct = product }
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Product")
                                        }
                                        IconButton(
                                            onClick = {
                                                viewModel.toggleProductArchive(product.productId, !product.active)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (product.active) Icons.Default.Archive else Icons.Default.Unarchive,
                                                contentDescription = if (product.active) "Archive" else "Reactivate",
                                                tint = if (product.active) MaterialTheme.colorScheme.onSurfaceVariant else StatusGreen
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                singleProductToDelete = product
                                            },
                                            modifier = Modifier.testTag("delete_product_button_${product.productId}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete Product",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallFloatingActionButton(
                onClick = { showBulkImportDialog = true },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = IndustrialOrange,
                modifier = Modifier.testTag("fab_import_csv")
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = "Import CSV")
            }

            FloatingActionButton(
                onClick = { showAddProductDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_product_fab")
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "Add Product")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Product", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Bulk Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        val count = selectedProductIds.size
        AlertDialog(
            onDismissRequest = {
                if (!isDeletingProducts) {
                    showDeleteConfirmDialog = false
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Delete $count Product${if (count != 1) "s" else ""}?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Are you sure you want to permanently delete $count selected product${if (count != 1) "s" else ""} from the catalogue?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "This will delete them from both the online Firestore database and local storage. This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    val productsToDelete = filteredProducts.filter { selectedProductIds.contains(it.productId) }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            productsToDelete.take(5).forEach { prod ->
                                Text(
                                    text = "• ${prod.name} (${prod.sku})",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                            if (productsToDelete.size > 5) {
                                Text(
                                    text = "... and ${productsToDelete.size - 5} more products",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isDeletingProducts = true
                            val idsToDelete = selectedProductIds.toList()
                            viewModel.deleteProducts(idsToDelete)
                            selectedProductIds.clear()
                            isSelectionMode = false
                            isDeletingProducts = false
                            showDeleteConfirmDialog = false
                        }
                    },
                    enabled = !isDeletingProducts,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_bulk_delete_button")
                ) {
                    if (isDeletingProducts) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Deleting...")
                    } else {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Permanently")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = false },
                    enabled = !isDeletingProducts
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Single Product Delete Confirmation Dialog
    if (singleProductToDelete != null) {
        val product = singleProductToDelete!!
        AlertDialog(
            onDismissRequest = {
                if (!isDeletingProducts) {
                    singleProductToDelete = null
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Delete Product?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Are you sure you want to permanently delete \"${product.name}\" (SKU: ${product.sku})?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "This will remove this product from the online Firestore database and local storage. This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isDeletingProducts = true
                            viewModel.deleteProduct(product.productId)
                            selectedProductIds.remove(product.productId)
                            isDeletingProducts = false
                            singleProductToDelete = null
                        }
                    },
                    enabled = !isDeletingProducts,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_single_delete_button")
                ) {
                    if (isDeletingProducts) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Deleting...")
                    } else {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { singleProductToDelete = null },
                    enabled = !isDeletingProducts
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Quick Stock Adjustment Dialog
    if (quickStockAdjustProduct != null) {
        val target = quickStockAdjustProduct!!
        AlertDialog(
            onDismissRequest = { quickStockAdjustProduct = null },
            title = { Text("Update Stock - ${target.name.take(20)}...") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Current: ${target.stockQuantity} ${target.unit}(s)", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = stockDeltaText,
                        onValueChange = { stockDeltaText = it },
                        label = { Text("Change Quantity (+/-)") },
                        supportingText = { Text("Use negative number like -2 to deduct") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = stockReasonText,
                        onValueChange = { stockReasonText = it },
                        label = { Text("Reason / Remarks") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val delta = stockDeltaText.toIntOrNull() ?: 0
                        if (delta != 0) {
                            viewModel.adjustStock(target.productId, delta, stockReasonText)
                        }
                        quickStockAdjustProduct = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndustrialOrange)
                ) {
                    Text("Save Stock")
                }
            },
            dismissButton = {
                TextButton(onClick = { quickStockAdjustProduct = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add / Edit Product Dialog
    if (showAddProductDialog || editingProduct != null) {
        val isEdit = editingProduct != null
        val existing = editingProduct
        val context = LocalContext.current
        ProductFormDialog(
            isEdit = isEdit,
            initialProduct = existing,
            categories = categories,
            brands = brands,
            onDismiss = {
                showAddProductDialog = false
                editingProduct = null
            },
            onSaveWithImages = { savedProduct, newUris, existingUrls, removedUrls, thumb ->
                viewModel.saveProductWithImages(
                    product = savedProduct,
                    isNew = !isEdit,
                    newLocalImageUris = newUris,
                    existingRemoteUrls = existingUrls,
                    removedRemoteUrls = removedUrls,
                    selectedThumbnail = thumb,
                    context = context
                )
            }
        )
    }

    // Bulk Import Dialog
    if (showBulkImportDialog) {
        BulkImportDialog(
            viewModel = viewModel,
            onDismiss = { showBulkImportDialog = false }
        )
    }
}

data class FormImageItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri? = null,
    val remoteUrl: String? = null,
    val isThumbnail: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormDialog(
    isEdit: Boolean,
    initialProduct: ProductEntity?,
    categories: List<CategoryEntity>,
    brands: List<com.example.data.model.BrandEntity>,
    onDismiss: () -> Unit,
    onSaveWithImages: suspend (
        product: ProductEntity,
        newLocalUris: List<Uri>,
        existingRemoteUrls: List<String>,
        removedRemoteUrls: List<String>,
        selectedThumbnail: String?
    ) -> Result<Unit>
) {
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Preserve the original productId strictly if editing; generate once if creating
    val targetProductId = remember(initialProduct?.productId) {
        initialProduct?.productId?.ifBlank { null }
            ?: "prod_${UUID.randomUUID().toString().replace("-", "").take(8)}"
    }

    var name by remember(initialProduct?.productId) { mutableStateOf(initialProduct?.name ?: "") }
    var sku by remember(initialProduct?.productId) { mutableStateOf(initialProduct?.sku ?: "") }
    var brandName by remember(initialProduct?.productId) { mutableStateOf(initialProduct?.brandName ?: (brands.firstOrNull()?.name ?: "Bosch")) }
    var categoryName by remember(initialProduct?.productId) { mutableStateOf(initialProduct?.categoryName ?: (categories.firstOrNull()?.name ?: "Grinder Machine")) }
    var brandDropdownExpanded by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var shortDesc by remember(initialProduct?.productId) { mutableStateOf(initialProduct?.shortDescription ?: "") }
    var fullDesc by remember(initialProduct?.productId) { mutableStateOf(initialProduct?.description ?: "") }
    var mrpText by remember(initialProduct?.productId) { mutableStateOf(initialProduct?.mrp?.toString() ?: "3000") }
    var sellingPriceText by remember(initialProduct?.productId) { mutableStateOf(initialProduct?.sellingPrice?.toString() ?: "2500") }
    var retailerPriceText by remember(initialProduct?.productId) { mutableStateOf(initialProduct?.retailerPrice?.toString() ?: "2200") }
    var gstText by remember(initialProduct?.productId) { mutableStateOf(initialProduct?.gstPercentage?.toString() ?: "18") }
    var stockText by remember(initialProduct?.productId) { mutableStateOf(initialProduct?.stockQuantity?.toString() ?: "10") }
    var minStockText by remember(initialProduct?.productId) { mutableStateOf(initialProduct?.minimumStockLevel?.toString() ?: "5") }
    var unit by remember(initialProduct?.productId) { mutableStateOf(initialProduct?.unit ?: "Piece") }
    var featured by remember(initialProduct?.productId) { mutableStateOf(initialProduct?.featured ?: false) }
    var active by remember(initialProduct?.productId) { mutableStateOf(initialProduct?.active ?: true) }

    var powerSpec by remember(initialProduct?.productId) {
        mutableStateOf(
            try {
                JSONObject(initialProduct?.specificationsJson ?: "{}").optString("Power", "670W")
            } catch (_: Exception) { "670W" }
        )
    }
    var voltageSpec by remember(initialProduct?.productId) {
        mutableStateOf(
            try {
                JSONObject(initialProduct?.specificationsJson ?: "{}").optString("Voltage", "220V")
            } catch (_: Exception) { "220V" }
        )
    }

    // Image list management: load existing images when editing
    var imageItems by remember(initialProduct?.productId) {
        val initialList = mutableListOf<FormImageItem>()
        if (initialProduct != null) {
            val remoteUrls = initialProduct.getImagesList()
            remoteUrls.forEach { url ->
                initialList.add(
                    FormImageItem(
                        remoteUrl = url,
                        isThumbnail = (url == initialProduct.thumbnailUrl || (initialList.isEmpty() && initialProduct.thumbnailUrl.isBlank()))
                    )
                )
            }
        }
        mutableStateOf(initialList.toList())
    }

    val removedRemoteUrls = remember(initialProduct?.productId) { mutableStateListOf<String>() }
    val context = LocalContext.current
    var showUrlDialog by remember { mutableStateOf(false) }
    var enteredImageUrl by remember { mutableStateOf("") }
    var fullscreenViewerIndex by remember { mutableStateOf<Int?>(null) }

    // Multi-image picker launcher using Android PhotoPicker (zero permission requirement)
    // with immediate local cache copying to prevent URI permission expiration
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            Log.d("ProductFormDialog", "IMAGE_SELECTED -> Received ${uris.size} URIs from photo picker for product $targetProductId")
            if (uris.isNotEmpty()) {
                val newItems = uris.map { uri ->
                    Log.d("ProductFormDialog", "IMAGE_SELECTED -> Staging local URI: $uri (Preview only, no upload yet)")
                    val safeUri = try {
                        val cacheFile = File(context.cacheDir, "pick_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            cacheFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        Uri.fromFile(cacheFile)
                    } catch (_: Exception) {
                        uri
                    }
                    FormImageItem(
                        uri = safeUri,
                        remoteUrl = null,
                        isThumbnail = false
                    )
                }
                val combined = imageItems + newItems
                // If no thumbnail yet, mark the first one as thumbnail
                imageItems = if (combined.none { it.isThumbnail } && combined.isNotEmpty()) {
                    combined.mapIndexed { idx, item -> item.copy(isThumbnail = (idx == 0)) }
                } else {
                    combined
                }
            }
        }
    )

    AlertDialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        title = {
            Column {
                Text(
                    text = if (isEdit) "Edit Tool Product" else "Add New Tool Product",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "ID: $targetProductId",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Error banner if any
                if (errorMessage != null) {
                    Surface(
                        color = StatusRedBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = StatusRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage ?: "",
                                color = StatusRed,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }

                // PRODUCT IMAGES SECTION
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Product Photos (${imageItems.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Tap to view fullscreen • Star for thumbnail",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { showUrlDialog = true },
                                    enabled = !isSaving,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("URL", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        galleryPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    enabled = !isSaving,
                                    colors = ButtonDefaults.buttonColors(containerColor = IndustrialOrange),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+ Add Photos", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (showUrlDialog) {
                            AlertDialog(
                                onDismissRequest = { showUrlDialog = false },
                                title = { Text("Add Image by URL", style = MaterialTheme.typography.titleMedium) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "Enter direct image URL (JPEG, PNG, or WebP):",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedTextField(
                                            value = enteredImageUrl,
                                            onValueChange = { enteredImageUrl = it },
                                            placeholder = { Text("https://example.com/image.jpg") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            if (enteredImageUrl.isNotBlank()) {
                                                val newItem = FormImageItem(
                                                    uri = null,
                                                    remoteUrl = enteredImageUrl.trim(),
                                                    isThumbnail = imageItems.isEmpty() || imageItems.none { it.isThumbnail }
                                                )
                                                imageItems = imageItems + newItem
                                                enteredImageUrl = ""
                                                showUrlDialog = false
                                            }
                                        }
                                    ) {
                                        Text("Add")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showUrlDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (imageItems.isEmpty()) {
                            Surface(
                                onClick = {
                                    galleryPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isSaving,
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.5.dp, IndustrialOrange.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = IndustrialOrange, modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("+ Add Photos", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = IndustrialOrange)
                                    Text("Select multiple photos from gallery", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(imageItems, key = { _, item -> item.id }) { index, item ->
                                    Box(
                                        modifier = Modifier
                                            .size(90.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(
                                                width = if (item.isThumbnail) 2.5.dp else 1.dp,
                                                color = if (item.isThumbnail) IndustrialOrange else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                // Tapping photo opens Fullscreen Viewer starting at this photo!
                                                fullscreenViewerIndex = index
                                            }
                                    ) {
                                        AsyncImage(
                                            model = ImageModelResolver.resolveImageModel(item.uri ?: item.remoteUrl),
                                            contentDescription = "Product Photo ${index + 1}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )

                                        // Star button for setting thumbnail (Top-Start)
                                        IconButton(
                                            onClick = {
                                                imageItems = imageItems.map { it.copy(isThumbnail = (it.id == item.id)) }
                                            },
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .size(26.dp)
                                                .background(
                                                    if (item.isThumbnail) IndustrialOrange else Color.Black.copy(alpha = 0.65f),
                                                    CircleShape
                                                )
                                        ) {
                                            Icon(
                                                imageVector = if (item.isThumbnail) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = if (item.isThumbnail) "Primary Thumbnail" else "Set as Thumbnail",
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }

                                        // Remove button (Top-End)
                                        IconButton(
                                            onClick = {
                                                if (item.remoteUrl != null) {
                                                    Log.d("ProductFormDialog", "IMAGE_REMOVE_PENDING -> Marked existing remote image for deletion on update: ${item.remoteUrl}")
                                                    removedRemoteUrls.add(item.remoteUrl)
                                                } else {
                                                    Log.d("ProductFormDialog", "IMAGE_REMOVE_LOCAL -> Dropped new local image from staging: ${item.uri}")
                                                }
                                                val remaining = imageItems.filterNot { it.id == item.id }
                                                imageItems = if (item.isThumbnail && remaining.isNotEmpty()) {
                                                    remaining.mapIndexed { idx, itm -> itm.copy(isThumbnail = (idx == 0)) }
                                                } else {
                                                    remaining
                                                }
                                            },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(26.dp)
                                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove Image",
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }

                                        // Thumbnail badge at bottom
                                        if (item.isThumbnail) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .fillMaxWidth()
                                                    .background(IndustrialOrange)
                                                    .padding(vertical = 2.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "★ MAIN",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                // Inline [+ Add Photos] card inside the gallery row!
                                item(key = "inline_add_photos_card") {
                                    Surface(
                                        onClick = {
                                            galleryPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        enabled = !isSaving,
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.5.dp, IndustrialOrange.copy(alpha = 0.7f)),
                                        modifier = Modifier.size(90.dp)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.fillMaxSize().padding(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AddPhotoAlternate,
                                                contentDescription = "Add Photos",
                                                tint = IndustrialOrange,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "+ Add",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = IndustrialOrange
                                            )
                                            Text(
                                                text = "Photos",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = IndustrialOrange
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Prominent [+ Add Photos] button below existing photos
                            Button(
                                onClick = {
                                    galleryPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isSaving,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IndustrialOrange),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("+ Add Photos", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Fullscreen image viewer when photo is tapped
                val activeFullscreenIndex = fullscreenViewerIndex
                if (activeFullscreenIndex != null && imageItems.isNotEmpty()) {
                    FullScreenImageViewer(
                        images = imageItems.map { it.uri ?: it.remoteUrl ?: "" },
                        initialIndex = activeFullscreenIndex.coerceIn(0, imageItems.size - 1),
                        thumbnailUrl = imageItems.find { it.isThumbnail }?.let { (it.uri ?: it.remoteUrl)?.toString() },
                        onSetThumbnail = { idx ->
                            val selected = imageItems.getOrNull(idx)
                            if (selected != null) {
                                imageItems = imageItems.map { it.copy(isThumbnail = (it.id == selected.id)) }
                            }
                        },
                        onDismiss = { fullscreenViewerIndex = null }
                    )
                }

                // BASIC DETAILS
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name *") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sku,
                    onValueChange = { sku = it },
                    label = { Text("SKU / Product Code *") },
                    placeholder = { Text("e.g. BOSCH-GWS600") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // BRAND dropdown
                    ExposedDropdownMenuBox(
                        expanded = brandDropdownExpanded,
                        onExpandedChange = { if (!isSaving) brandDropdownExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = brandName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Brand") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = brandDropdownExpanded) },
                            enabled = !isSaving,
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = brandDropdownExpanded,
                            onDismissRequest = { brandDropdownExpanded = false }
                        ) {
                            brands.forEach { brand ->
                                DropdownMenuItem(
                                    text = { Text(brand.name) },
                                    onClick = {
                                        brandName = brand.name
                                        brandDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // CATEGORY dropdown
                    ExposedDropdownMenuBox(
                        expanded = categoryDropdownExpanded,
                        onExpandedChange = { if (!isSaving) categoryDropdownExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = categoryName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                            enabled = !isSaving,
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = categoryDropdownExpanded,
                            onDismissRequest = { categoryDropdownExpanded = false }
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        categoryName = cat.name
                                        categoryDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // PRICING SECTION
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = mrpText,
                        onValueChange = { mrpText = it },
                        label = { Text("MRP (₹)") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = sellingPriceText,
                        onValueChange = { sellingPriceText = it },
                        label = { Text("Selling Price (₹)") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = retailerPriceText,
                        onValueChange = { retailerPriceText = it },
                        label = { Text("Retailer Price (₹) *") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = gstText,
                        onValueChange = { gstText = it },
                        label = { Text("GST %") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f)
                    )
                }

                // INVENTORY SECTION
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = stockText,
                        onValueChange = { stockText = it },
                        label = { Text("Stock Qty") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minStockText,
                        onValueChange = { minStockText = it },
                        label = { Text("Min Alert Level") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f)
                    )
                }

                // DESCRIPTIONS
                OutlinedTextField(
                    value = shortDesc,
                    onValueChange = { shortDesc = it },
                    label = { Text("Short Description") },
                    maxLines = 2,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fullDesc,
                    onValueChange = { fullDesc = it },
                    label = { Text("Detailed Description") },
                    maxLines = 4,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )

                // TECHNICAL SPECIFICATIONS
                Text(
                    text = "Key Specifications",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = powerSpec,
                        onValueChange = { powerSpec = it },
                        label = { Text("Power / Wattage") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = voltageSpec,
                        onValueChange = { voltageSpec = it },
                        label = { Text("Voltage / Speed") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f)
                    )
                }

                // STATUS TOGGLES
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = featured,
                            onCheckedChange = { featured = it },
                            enabled = !isSaving
                        )
                        Text("Featured Product", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = active,
                            onCheckedChange = { active = it },
                            enabled = !isSaving
                        )
                        Text("Active in Catalog", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Uploading progress indicator
                if (isSaving) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = IndustrialOrange,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isEdit) "Uploading photos & saving updates to Firestore..." else "Uploading photos & creating in Firestore...",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = IndustrialOrange
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = "Product name cannot be empty."
                        return@Button
                    }
                    if (sku.isBlank()) {
                        errorMessage = "SKU cannot be empty."
                        return@Button
                    }

                    val mrp = mrpText.toDoubleOrNull() ?: 0.0
                    val selling = sellingPriceText.toDoubleOrNull() ?: 0.0
                    val retailer = retailerPriceText.toDoubleOrNull() ?: selling
                    val gst = gstText.toDoubleOrNull() ?: 18.0
                    val stock = stockText.toIntOrNull() ?: 0
                    val minStock = minStockText.toIntOrNull() ?: 5

                    val specsObj = JSONObject().apply {
                        put("Power", powerSpec.trim())
                        put("Voltage", voltageSpec.trim())
                    }

                    val matchingCat = categories.find { it.name.equals(categoryName.trim(), ignoreCase = true) }
                    val matchingBrand = brands.find { it.name.equals(brandName.trim(), ignoreCase = true) }

                    val base = initialProduct ?: ProductEntity(
                        productId = targetProductId,
                        name = name.trim(),
                        slug = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-"),
                        sku = sku.trim(),
                        brandId = matchingBrand?.brandId ?: "br_general",
                        brandName = brandName.trim(),
                        categoryId = matchingCat?.categoryId ?: "cat_general",
                        categoryName = categoryName.trim(),
                        mrp = mrp,
                        sellingPrice = selling,
                        retailerPrice = retailer,
                        gstPercentage = gst,
                        stockQuantity = stock,
                        minimumStockLevel = minStock,
                        unit = unit.trim()
                    )

                    val finalProduct = base.copy(
                        productId = targetProductId,
                        name = name.trim(),
                        slug = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-"),
                        sku = sku.trim(),
                        brandId = matchingBrand?.brandId ?: base.brandId,
                        brandName = brandName.trim(),
                        categoryId = matchingCat?.categoryId ?: base.categoryId,
                        categoryName = categoryName.trim(),
                        shortDescription = shortDesc.trim(),
                        description = fullDesc.trim(),
                        mrp = mrp,
                        sellingPrice = selling,
                        retailerPrice = retailer,
                        gstPercentage = gst,
                        stockQuantity = stock,
                        minimumStockLevel = minStock,
                        unit = unit.trim(),
                        featured = featured,
                        active = active,
                        specificationsJson = specsObj.toString()
                    )

                    val newLocalUris = imageItems.mapNotNull { it.uri }
                    val existingRemoteUrls = imageItems.mapNotNull { it.remoteUrl }
                    val selectedThumb = imageItems.find { it.isThumbnail }?.let { it.uri?.toString() ?: it.remoteUrl }

                    coroutineScope.launch {
                        isSaving = true
                        errorMessage = null
                        val result = onSaveWithImages(
                            finalProduct,
                            newLocalUris,
                            existingRemoteUrls,
                            removedRemoteUrls.toList(),
                            selectedThumb
                        )
                        isSaving = false
                        if (result.isSuccess) {
                            onDismiss()
                        } else {
                            errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Failed to save product"
                        }
                    }
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = IndustrialOrange)
            ) {
                Text(if (isEdit) "Update Product" else "Save Product")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text("Cancel")
            }
        }
    )
}
