package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.OrderStatus
import com.example.data.model.StockStatus
import com.example.ui.AdminNavDestination
import com.example.ui.AnuToolsViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun DashboardScreen(
    viewModel: AnuToolsViewModel,
    onNavigate: (AdminNavDestination) -> Unit
) {
    val products by viewModel.products.collectAsState(initial = emptyList())
    val orders by viewModel.orders.collectAsState(initial = emptyList())

    val activeProducts = remember(products) { products.filter { it.active } }
    val lowStockProducts = remember(products) { products.filter { it.stockStatus == StockStatus.LOW_STOCK } }
    val outOfStockProducts = remember(products) { products.filter { it.stockStatus == StockStatus.OUT_OF_STOCK } }

    val pendingOrders = remember(orders) { orders.filter { it.orderStatus == OrderStatus.PENDING } }
    val confirmedOrders = remember(orders) { orders.filter { it.orderStatus == OrderStatus.CONFIRMED } }
    val outForDeliveryOrders = remember(orders) { orders.filter { it.orderStatus == OrderStatus.OUT_FOR_DELIVERY } }
    val completedOrders = remember(orders) { orders.filter { it.orderStatus == OrderStatus.DELIVERED } }
    val cancelledOrders = remember(orders) { orders.filter { it.orderStatus == OrderStatus.CANCELLED } }

    var quickRestockProductId by remember { mutableStateOf<String?>(null) }
    var restockQuantityText by remember { mutableStateOf("10") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Business Welcome Banner with Live Simulation CTA
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0F224A) // Deep Cobalt Navy
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ANU TOOLS & SERVICE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    fontSize = 10.5.sp
                                ),
                                color = Color(0xFF60A5FA),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Dashboard",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .width(64.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_anu_logo),
                                    contentDescription = "Anu Tools Logo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Real-time sync connected with Retailer app. Orders placed in Firestore (collection 'orders') arrive automatically via snapshot listener.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                        color = Color(0xFFE2E8F0)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onNavigate(AdminNavDestination.ORDERS) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View Live Orders (${orders.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Section: Primary Inventory Metrics
        item {
            Text(
                text = "CATALOG & INVENTORY HEALTH",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Total Active",
                    value = "${activeProducts.size}",
                    subtitle = "Catalog Items",
                    icon = Icons.Default.Construction,
                    iconBgColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(AdminNavDestination.PRODUCTS) }
                )
                MetricCard(
                    title = "Low Stock",
                    value = "${lowStockProducts.size}",
                    subtitle = "Needs Restock",
                    icon = Icons.Default.Warning,
                    iconBgColor = StatusAmberBg,
                    iconTint = StatusAmber,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(AdminNavDestination.INVENTORY) }
                )
                MetricCard(
                    title = "Out of Stock",
                    value = "${outOfStockProducts.size}",
                    subtitle = "Zero Units",
                    icon = Icons.Default.RemoveCircleOutline,
                    iconBgColor = StatusRedBg,
                    iconTint = StatusRed,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(AdminNavDestination.INVENTORY) }
                )
            }
        }

        // Section: Order Pipeline Workflow
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ORDER FULFILLMENT PIPELINE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { onNavigate(AdminNavDestination.ORDERS) }) {
                    Text("View All (${orders.size})", style = MaterialTheme.typography.labelMedium)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        title = "Pending Orders",
                        value = "${pendingOrders.size}",
                        subtitle = "To Confirm",
                        icon = Icons.Default.HourglassTop,
                        iconBgColor = StatusAmberBg,
                        iconTint = StatusAmber,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AdminNavDestination.ORDERS) }
                    )
                    MetricCard(
                        title = "Confirmed Orders",
                        value = "${confirmedOrders.size}",
                        subtitle = "Ready to Pack",
                        icon = Icons.Default.CheckCircle,
                        iconBgColor = StatusBlueBg,
                        iconTint = StatusBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AdminNavDestination.ORDERS) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        title = "Out for Delivery",
                        value = "${outForDeliveryOrders.size}",
                        subtitle = "In Transit",
                        icon = Icons.Default.LocalShipping,
                        iconBgColor = StatusAmberBg,
                        iconTint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AdminNavDestination.ORDERS) }
                    )
                    MetricCard(
                        title = "Completed",
                        value = "${completedOrders.size}",
                        subtitle = "Delivered",
                        icon = Icons.Default.DoneAll,
                        iconBgColor = StatusGreenBg,
                        iconTint = StatusGreen,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(AdminNavDestination.ORDERS) }
                    )
                }
            }
        }

        // Section: Urgent Restock Alerts
        if (lowStockProducts.isNotEmpty() || outOfStockProducts.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RESTOCK ALERTS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = StatusRed
                    )
                    Text(
                        text = "${lowStockProducts.size + outOfStockProducts.size} items",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                val alerts = (outOfStockProducts + lowStockProducts).take(3)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    alerts.forEach { product ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(10.dp),
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
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "SKU: ${product.sku} • Min Level: ${product.minimumStockLevel}",
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
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = {
                                        quickRestockProductId = product.productId
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Restock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Recent Orders
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT ORDERS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { onNavigate(AdminNavDestination.ORDERS) }) {
                    Text("Manage", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (orders.isEmpty()) {
                EmptyStateView(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = "No Orders Yet",
                    description = "When retailers submit orders, they will instantly appear here."
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    orders.take(4).forEach { order ->
                        Card(
                            onClick = {
                                viewModel.openOrderDetails(order.orderId)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = order.orderNumber,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    OrderStatusBadge(status = order.orderStatus)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = order.shopName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${order.retailerName} • ${order.phone} • ${order.city}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = formatCurrency(order.total),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = IndustrialOrange
                                            )
                                        )
                                        Text(
                                            text = "${order.itemsCount} items • ${order.paymentMethod.displayName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    PaymentStatusBadge(status = order.paymentStatus)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Quick Restock Dialog
    if (quickRestockProductId != null) {
        val targetProduct = products.find { it.productId == quickRestockProductId }
        AlertDialog(
            onDismissRequest = { quickRestockProductId = null },
            title = { Text("Restock ${targetProduct?.name?.take(25)}...") },
            text = {
                Column {
                    Text(
                        text = "Current Stock: ${targetProduct?.stockQuantity} ${targetProduct?.unit}(s)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = restockQuantityText,
                        onValueChange = { restockQuantityText = it },
                        label = { Text("Quantity to Add") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = restockQuantityText.toIntOrNull() ?: 10
                        if (targetProduct != null && qty > 0) {
                            viewModel.adjustStock(
                                productId = targetProduct.productId,
                                change = qty,
                                reason = "Inward restock shipment batch"
                            )
                        }
                        quickRestockProductId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndustrialOrange)
                ) {
                    Text("Confirm Restock")
                }
            },
            dismissButton = {
                TextButton(onClick = { quickRestockProductId = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
