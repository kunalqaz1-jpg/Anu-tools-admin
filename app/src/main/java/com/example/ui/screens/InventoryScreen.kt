package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.InventoryTransactionEntity
import com.example.data.model.ProductEntity
import com.example.data.model.StockStatus
import com.example.data.model.TransactionType
import com.example.ui.AnuToolsViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun InventoryScreen(
    viewModel: AnuToolsViewModel,
    onOpenProductDetail: (String) -> Unit
) {
    val products by viewModel.products.collectAsState(initial = emptyList())
    val transactions by viewModel.inventoryTransactions.collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(0) } // 0: Stock Overview, 1: Transaction History
    var quickAdjustProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var adjustDeltaText by remember { mutableStateOf("10") }
    var adjustReasonText by remember { mutableStateOf("Restock shipment") }

    val lowStockCount = remember(products) { products.count { it.stockStatus == StockStatus.LOW_STOCK } }
    val outOfStockCount = remember(products) { products.count { it.stockStatus == StockStatus.OUT_OF_STOCK } }
    val inStockCount = remember(products) { products.count { it.stockStatus == StockStatus.IN_STOCK } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Inventory Control",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Live stock tracking with automatic deduction on order confirmation",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Inventory Metric Highlights
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "In Stock",
                    value = "$inStockCount",
                    icon = Icons.Default.CheckCircle,
                    iconBgColor = StatusGreenBg,
                    iconTint = StatusGreen,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Low Stock",
                    value = "$lowStockCount",
                    icon = Icons.Default.Warning,
                    iconBgColor = StatusAmberBg,
                    iconTint = StatusAmber,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Out of Stock",
                    value = "$outOfStockCount",
                    icon = Icons.Default.RemoveCircleOutline,
                    iconBgColor = StatusRedBg,
                    iconTint = StatusRed,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = IndustrialOrange
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Stock Status", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Audit Trail (${transactions.size})", fontWeight = FontWeight.Bold) }
                )
            }
        }

        HorizontalDivider()

        if (selectedTab == 0) {
            // Stock list sorted with Out of Stock & Low Stock first!
            val sortedProducts = remember(products) {
                products.sortedWith(
                    compareBy<ProductEntity> {
                        when (it.stockStatus) {
                            StockStatus.OUT_OF_STOCK -> 0
                            StockStatus.LOW_STOCK -> 1
                            StockStatus.IN_STOCK -> 2
                        }
                    }.thenBy { it.stockQuantity }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sortedProducts, key = { it.productId }) { product ->
                    Card(
                        onClick = { onOpenProductDetail(product.productId) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = product.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1
                                )
                                Text(
                                    text = "SKU: ${product.sku} • Min: ${product.minimumStockLevel} ${product.unit}(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                StockStatusBadge(
                                    status = product.stockStatus,
                                    quantity = product.stockQuantity,
                                    unit = product.unit
                                )
                            }
                            Button(
                                onClick = {
                                    quickAdjustProduct = product
                                    adjustDeltaText = "10"
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = IndustrialOrange),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Adjust")
                            }
                        }
                    }
                }
            }
        } else {
            // Transaction History
            if (transactions.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.History,
                    title = "No Inventory Transactions",
                    description = "Transactions will record whenever orders are confirmed, cancelled, or stock is adjusted."
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(transactions, key = { it.transactionId }) { tx ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tx.productName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${tx.type.name.replace("_", " ")}: ${tx.reason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${formatDateTime(tx.createdAt)} • By ${tx.createdBy}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    color = if (tx.quantity >= 0) StatusGreenBg else StatusRedBg,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (tx.quantity >= 0) "+${tx.quantity}" else "${tx.quantity}",
                                        color = if (tx.quantity >= 0) StatusGreen else StatusRed,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Quick Adjust Dialog
    if (quickAdjustProduct != null) {
        val target = quickAdjustProduct!!
        AlertDialog(
            onDismissRequest = { quickAdjustProduct = null },
            title = { Text("Update Stock - ${target.name.take(20)}...") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current stock: ${target.stockQuantity} ${target.unit}(s)", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = adjustDeltaText,
                        onValueChange = { adjustDeltaText = it },
                        label = { Text("Quantity change (+/-)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = adjustReasonText,
                        onValueChange = { adjustReasonText = it },
                        label = { Text("Reason / Remarks") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val delta = adjustDeltaText.toIntOrNull() ?: 0
                        if (delta != 0) {
                            viewModel.adjustStock(target.productId, delta, adjustReasonText)
                        }
                        quickAdjustProduct = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndustrialOrange)
                ) {
                    Text("Apply Adjustment")
                }
            },
            dismissButton = {
                TextButton(onClick = { quickAdjustProduct = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
