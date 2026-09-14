package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.data.model.RetailerStatus
import com.example.ui.AnuToolsViewModel
import com.example.ui.components.*
import com.example.ui.theme.IndustrialOrange
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusGreenBg
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusRedBg

@Composable
fun RetailersScreen(viewModel: AnuToolsViewModel) {
    val retailers by viewModel.retailers.collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }

    val filteredRetailers = remember(retailers, searchQuery) {
        retailers.filter { ret ->
            searchQuery.isBlank() ||
                    ret.shopName.contains(searchQuery, ignoreCase = true) ||
                    ret.name.contains(searchQuery, ignoreCase = true) ||
                    ret.city.contains(searchQuery, ignoreCase = true) ||
                    ret.phone.contains(searchQuery, ignoreCase = true) ||
                    ret.email.contains(searchQuery, ignoreCase = true)
        }
    }

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
                text = "Retailer Accounts",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Hardware stores & authorized machinery partners",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by shop, owner, city, email or phone...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        HorizontalDivider()

        if (filteredRetailers.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Storefront,
                title = "No Retailers Found",
                description = "Retailers registered via the retailer app or Firestore will appear here in real time."
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredRetailers, key = { it.retailerId }) { ret ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openRetailerDetails(ret.retailerId) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // Round profile photo or initials
                                    val photoUrl = ret.profilePhotoUrl.trim()
                                    val hasValidPhoto = photoUrl.isNotBlank() &&
                                            (photoUrl.startsWith("http://", ignoreCase = true) || photoUrl.startsWith("https://", ignoreCase = true))

                                    if (hasValidPhoto) {
                                        SubcomposeAsyncImage(
                                            model = photoUrl,
                                            contentDescription = "Retailer Photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .border(1.5.dp, IndustrialOrange, CircleShape),
                                            loading = {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                                )
                                            },
                                            error = {
                                                RetailerListInitials(ret.name, ret.shopName)
                                            }
                                        )
                                    } else {
                                        RetailerListInitials(ret.name, ret.shopName)
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ret.shopName,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Owner: ${ret.name} • ${ret.phone}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (ret.email.isNotBlank()) {
                                            Text(
                                                text = ret.email,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        val fullAddr = buildString {
                                            if (ret.address.isNotBlank()) append(ret.address)
                                            if (ret.city.isNotBlank()) {
                                                if (isNotEmpty()) append(", ")
                                                append(ret.city)
                                            }
                                            if (ret.state.isNotBlank()) {
                                                if (isNotEmpty()) append(", ")
                                                append(ret.state)
                                            }
                                            if (ret.pincode.isNotBlank()) {
                                                if (isNotEmpty()) append(" - ")
                                                append(ret.pincode)
                                            }
                                        }
                                        if (fullAddr.isNotBlank()) {
                                            Text(
                                                text = fullAddr,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Surface(
                                    color = if (ret.status == RetailerStatus.ACTIVE) StatusGreenBg else StatusRedBg,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = ret.status.displayName,
                                        color = if (ret.status == RetailerStatus.ACTIVE) StatusGreen else StatusRed,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

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
                                        text = "Total Orders",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${ret.totalOrders} orders placed",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Lifetime Business",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatCurrency(ret.totalBusinessValue),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = IndustrialOrange
                                        )
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

@Composable
private fun RetailerListInitials(name: String, shopName: String) {
    val initial = (name.trim().take(1).ifBlank { shopName.trim().take(1) }).uppercase()
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (initial.isNotBlank()) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = IndustrialOrange
                )
            )
        } else {
            Icon(
                Icons.Default.Storefront,
                contentDescription = null,
                tint = IndustrialOrange,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

