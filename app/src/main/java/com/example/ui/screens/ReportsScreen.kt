package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OrderStatus
import com.example.data.model.RetailerStatus
import com.example.data.model.StockStatus
import com.example.ui.AnuToolsViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun ReportsScreen(viewModel: AnuToolsViewModel) {
    val orders by viewModel.orders.collectAsState(initial = emptyList())
    val products by viewModel.products.collectAsState(initial = emptyList())
    val retailers by viewModel.retailers.collectAsState(initial = emptyList())

    // 1. Orders Analytics
    val totalOrdersCount = orders.size
    val deliveredOrders = remember(orders) { orders.filter { it.orderStatus == OrderStatus.DELIVERED } }
    val deliveredOrdersCount = deliveredOrders.size
    val pendingOrdersCount = remember(orders) { orders.count { it.orderStatus == OrderStatus.PENDING } }
    val cancelledOrdersCount = remember(orders) { orders.count { it.orderStatus == OrderStatus.CANCELLED } }

    // Gross Revenue = sum of delivered/completed orders
    val grossRevenue = remember(deliveredOrders) { deliveredOrders.sumOf { it.total } }
    val avgOrderValue = remember(deliveredOrdersCount, grossRevenue) {
        if (deliveredOrdersCount > 0) grossRevenue / deliveredOrdersCount else 0.0
    }

    // 2. Products & Inventory Analytics
    val totalProductsCount = products.size
    val inStockProductsCount = remember(products) { products.count { it.stockQuantity > 0 && it.stockStatus == StockStatus.IN_STOCK } }
    val outOfStockProductsCount = remember(products) { products.count { it.stockQuantity <= 0 || it.stockStatus == StockStatus.OUT_OF_STOCK } }
    val totalInventoryValue = remember(products) {
        products.sumOf { it.stockQuantity.coerceAtLeast(0) * it.retailerPrice }
    }

    // 3. Retailers Analytics
    val totalRetailersCount = retailers.size
    val activeRetailersCount = remember(retailers) { retailers.count { it.status == RetailerStatus.ACTIVE } }
    val inactiveRetailersCount = totalRetailersCount - activeRetailersCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Reports & Analytics",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Live analytics computed from real Firestore collections",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ━━━━━━ SECTION 1: ORDERS ANALYTICS ━━━━━━
            item {
                SectionHeader(title = "ORDERS & REVENUE ANALYTICS", subtitle = "From orders collection")
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Gross Revenue",
                        value = formatCurrency(grossRevenue),
                        subtitle = "Sum of delivered orders",
                        icon = Icons.Default.CurrencyRupee,
                        iconBgColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = IndustrialOrange,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Avg Order Value",
                        value = formatCurrency(avgOrderValue),
                        subtitle = "Per delivered order",
                        icon = Icons.Default.TrendingUp,
                        iconBgColor = StatusGreenBg,
                        iconTint = StatusGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Total Orders",
                        value = "$totalOrdersCount",
                        subtitle = "All orders placed",
                        icon = Icons.Default.ReceiptLong,
                        iconBgColor = StatusBlueBg,
                        iconTint = StatusBlue,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Delivered",
                        value = "$deliveredOrdersCount",
                        subtitle = "Fulfilled & settled",
                        icon = Icons.Default.CheckCircle,
                        iconBgColor = StatusGreenBg,
                        iconTint = StatusGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Pending Orders",
                        value = "$pendingOrdersCount",
                        subtitle = "Awaiting verification/dispatch",
                        icon = Icons.Default.HourglassTop,
                        iconBgColor = StatusAmberBg,
                        iconTint = StatusAmber,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Cancelled Orders",
                        value = "$cancelledOrdersCount",
                        subtitle = "Voided orders",
                        icon = Icons.Default.Cancel,
                        iconBgColor = StatusRedBg,
                        iconTint = StatusRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ━━━━━━ SECTION 2: PRODUCTS & INVENTORY ANALYTICS ━━━━━━
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SectionHeader(title = "PRODUCTS & INVENTORY VALUE", subtitle = "From products collection")
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Total Inventory Value",
                        value = formatCurrency(totalInventoryValue),
                        subtitle = "Stock Qty × Retailer Price",
                        icon = Icons.Default.AccountBalanceWallet,
                        iconBgColor = StatusPurpleBg,
                        iconTint = StatusPurple,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Total Products",
                        value = "$totalProductsCount",
                        subtitle = "Catalog SKUs",
                        icon = Icons.Default.Construction,
                        iconBgColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconTint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "In Stock Products",
                        value = "$inStockProductsCount",
                        subtitle = "Available for ordering",
                        icon = Icons.Default.Inventory2,
                        iconBgColor = StatusGreenBg,
                        iconTint = StatusGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Out of Stock",
                        value = "$outOfStockProductsCount",
                        subtitle = "Requires restocking",
                        icon = Icons.Default.WarningAmber,
                        iconBgColor = StatusRedBg,
                        iconTint = StatusRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ━━━━━━ SECTION 3: RETAILER ACCOUNTS ANALYTICS ━━━━━━
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SectionHeader(title = "RETAILER ACCOUNTS", subtitle = "From retailers collection")
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Total Retailers",
                        value = "$totalRetailersCount",
                        subtitle = "Registered accounts",
                        icon = Icons.Default.Storefront,
                        iconBgColor = StatusBlueBg,
                        iconTint = StatusBlue,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Active Retailers",
                        value = "$activeRetailersCount",
                        subtitle = if (inactiveRetailersCount > 0) "$inactiveRetailersCount inactive" else "All accounts active",
                        icon = Icons.Default.VerifiedUser,
                        iconBgColor = StatusGreenBg,
                        iconTint = StatusGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Top Products Card
            if (products.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "TOP MOVING TOOLS & CONSUMABLES",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            val topProducts = products.sortedByDescending { it.mrp }.take(4)
                            topProducts.forEachIndexed { index, prod ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "#${index + 1}",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(prod.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                                            Text("${prod.brandName} • Stock: ${prod.stockQuantity}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Text(formatCurrency(prod.retailerPrice), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = IndustrialOrange))
                                }
                                if (index < topProducts.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Top Retailer Partners Card
            if (retailers.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "TOP RETAILER PARTNERS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            val topRetailers = retailers.sortedByDescending { it.totalBusinessValue }.take(4)
                            topRetailers.forEachIndexed { index, ret ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                color = IndustrialOrange,
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(ret.shopName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                            Text("${ret.city} • ${ret.totalOrders} orders", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Text(formatCurrency(ret.totalBusinessValue), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                }
                                if (index < topRetailers.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
