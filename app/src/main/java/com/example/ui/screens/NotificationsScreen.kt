package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.example.data.model.NotificationType
import com.example.ui.AdminNavDestination
import com.example.ui.AnuToolsViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun NotificationsScreen(
    viewModel: AnuToolsViewModel,
    onNavigate: (AdminNavDestination) -> Unit
) {
    val notifications by viewModel.notifications.collectAsState(initial = emptyList())
    val unreadCount by viewModel.unreadCount.collectAsState(initial = 0)

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "$unreadCount unread alerts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (unreadCount > 0) {
                    TextButton(onClick = { viewModel.markAllNotificationsAsRead() }) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mark all read")
                    }
                }
            }
        }

        HorizontalDivider()

        if (notifications.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.NotificationsNone,
                title = "No Notifications",
                description = "You're all caught up! New order alerts and stock warnings will show here."
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications, key = { it.notificationId }) { notif ->
                    val (icon, tint, bg) = when (notif.type) {
                        NotificationType.NEW_ORDER -> Triple(Icons.Default.Receipt, IndustrialOrange, MaterialTheme.colorScheme.primaryContainer)
                        NotificationType.LOW_STOCK -> Triple(Icons.Default.Warning, StatusAmber, StatusAmberBg)
                        NotificationType.OUT_OF_STOCK -> Triple(Icons.Default.Error, StatusRed, StatusRedBg)
                        NotificationType.ORDER_CANCELLED -> Triple(Icons.Default.Cancel, StatusRed, StatusRedBg)
                        NotificationType.PAYMENT_SUBMITTED -> Triple(Icons.Default.Payment, StatusGreen, StatusGreenBg)
                    }

                    Card(
                        onClick = {
                            viewModel.markNotificationAsRead(notif.notificationId)
                            if (notif.orderId != null) {
                                viewModel.openOrderDetails(notif.orderId)
                                onNavigate(AdminNavDestination.ORDERS)
                            } else if (notif.productId != null) {
                                viewModel.openProductDetails(notif.productId)
                                onNavigate(AdminNavDestination.PRODUCTS)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (!notif.read) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = if (!notif.read) CardDefaults.outlinedCardBorder() else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = notif.title,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = if (!notif.read) FontWeight.Bold else FontWeight.SemiBold
                                        )
                                    )
                                    if (!notif.read) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(IndustrialOrange)
                                        )
                                    }
                                }
                                Text(
                                    text = notif.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatDateTime(notif.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
