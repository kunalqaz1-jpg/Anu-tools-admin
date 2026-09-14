package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.UserRole
import com.example.ui.AdminNavDestination
import com.example.ui.AnuToolsViewModel
import com.example.ui.theme.IndustrialOrange
import com.example.ui.theme.SlateMedium

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminTopBar(
    viewModel: AnuToolsViewModel,
    onMenuClick: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState(initial = 0)
    var showUserMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier
                        .height(34.dp)
                        .width(60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = Color.White,
                    shadowElevation = 1.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_anu_logo),
                            contentDescription = "Anu Tools Logo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "Anu Tools & Service",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 0.2.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "ADMIN PORTAL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.5.sp,
                            letterSpacing = 0.8.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
            }
        },
        actions = {
            // Notification Bell with badge
            IconButton(
                onClick = { viewModel.navigateTo(AdminNavDestination.NOTIFICATIONS) },
                modifier = Modifier.size(40.dp)
            ) {
                BadgedBox(
                    badge = {
                        if (unreadCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            ) {
                                Text("$unreadCount")
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // User Role & Profile Switcher
            Box {
                Surface(
                    onClick = { showUserMenu = true },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, end = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = authState.currentUser.name.take(1),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        RoleBadge(role = authState.currentUser.role)
                    }
                }

                DropdownMenu(
                    expanded = showUserMenu,
                    onDismissRequest = { showUserMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = authState.currentUser.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = authState.currentUser.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = { },
                        leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) }
                    )
                    HorizontalDivider()
                    Text(
                        text = "SWITCH ROLE (DEMO)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    DropdownMenuItem(
                        text = { Text("Administrator") },
                        onClick = {
                            viewModel.switchRole(UserRole.SUPER_ADMIN)
                            showUserMenu = false
                        },
                        leadingIcon = {
                            if (authState.currentUser.role == UserRole.SUPER_ADMIN) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = IndustrialOrange)
                            } else {
                                Spacer(modifier = Modifier.size(24.dp))
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Store Manager (Admin)") },
                        onClick = {
                            viewModel.switchRole(UserRole.ADMIN)
                            showUserMenu = false
                        },
                        leadingIcon = {
                            if (authState.currentUser.role == UserRole.ADMIN) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = IndustrialOrange)
                            } else {
                                Spacer(modifier = Modifier.size(24.dp))
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Dispatch & Inventory (Staff)") },
                        onClick = {
                            viewModel.switchRole(UserRole.STAFF)
                            showUserMenu = false
                        },
                        leadingIcon = {
                            if (authState.currentUser.role == UserRole.STAFF) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = IndustrialOrange)
                            } else {
                                Spacer(modifier = Modifier.size(24.dp))
                            }
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Audit Trail", color = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            viewModel.navigateTo(AdminNavDestination.AUDIT_LOG)
                            showUserMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Logout", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            viewModel.logout()
                            showUserMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun AdminBottomNav(
    currentNav: AdminNavDestination,
    onNavigate: (AdminNavDestination) -> Unit,
    onOpenMoreMenu: () -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        windowInsets = WindowInsets.navigationBars
    ) {
        val navItems = listOf(
            Triple(AdminNavDestination.DASHBOARD, Icons.Default.Dashboard, "Dashboard"),
            Triple(AdminNavDestination.ORDERS, Icons.AutoMirrored.Filled.ReceiptLong, "Orders"),
            Triple(AdminNavDestination.PRODUCTS, Icons.Default.Construction, "Products"),
            Triple(AdminNavDestination.INVENTORY, Icons.Default.Inventory2, "Inventory")
        )

        navItems.forEach { (dest, icon, label) ->
            NavigationBarItem(
                selected = currentNav == dest,
                onClick = { onNavigate(dest) },
                icon = { Icon(icon, contentDescription = label) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        maxLines = 1,
                        softWrap = false
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }

        val isMoreSelected = currentNav in listOf(
            AdminNavDestination.CATEGORIES_BRANDS,
            AdminNavDestination.RETAILERS,
            AdminNavDestination.REPORTS,
            AdminNavDestination.SETTINGS,
            AdminNavDestination.AUDIT_LOG,
            AdminNavDestination.NOTIFICATIONS
        )

        NavigationBarItem(
            selected = isMoreSelected,
            onClick = onOpenMoreMenu,
            icon = { Icon(Icons.Default.Apps, contentDescription = "More") },
            label = {
                Text(
                    text = "More",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    maxLines = 1,
                    softWrap = false
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

@Composable
fun AdminNavigationDrawerContent(
    currentNav: AdminNavDestination,
    onSelectDestination: (AdminNavDestination) -> Unit,
    onCloseDrawer: () -> Unit,
    currentUser: com.example.data.model.UserEntity
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(300.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier
                        .height(44.dp)
                        .width(76.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    color = Color.White,
                    shadowElevation = 1.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_anu_logo),
                            contentDescription = "Anu Tools Logo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Anu Tools",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "& Service Center",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = IndustrialOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = currentUser.name,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1
                        )
                        RoleBadge(role = currentUser.role)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val menuItems = listOf(
            Triple(AdminNavDestination.DASHBOARD, Icons.Default.Dashboard, "Dashboard"),
            Triple(AdminNavDestination.ORDERS, Icons.AutoMirrored.Filled.ReceiptLong, "Order Management"),
            Triple(AdminNavDestination.PRODUCTS, Icons.Default.Construction, "Product Catalogue"),
            Triple(AdminNavDestination.INVENTORY, Icons.Default.Inventory2, "Inventory Control"),
            Triple(AdminNavDestination.CATEGORIES_BRANDS, Icons.Default.Category, "Categories & Brands"),
            Triple(AdminNavDestination.RETAILERS, Icons.Default.Storefront, "Retailer Accounts"),
            Triple(AdminNavDestination.REPORTS, Icons.Default.BarChart, "Sales & Reports"),
            Triple(AdminNavDestination.NOTIFICATIONS, Icons.Default.Notifications, "Notification Center"),
            Triple(AdminNavDestination.AUDIT_LOG, Icons.Default.History, "Audit Logs"),
            Triple(AdminNavDestination.SETTINGS, Icons.Default.Settings, "Business Settings")
        )

        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            menuItems.forEach { (dest, icon, label) ->
                NavigationDrawerItem(
                    icon = { Icon(icon, contentDescription = null) },
                    label = { Text(label, fontWeight = if (currentNav == dest) FontWeight.Bold else FontWeight.Normal) },
                    selected = currentNav == dest,
                    onClick = {
                        onSelectDestination(dest)
                        onCloseDrawer()
                    },
                    modifier = Modifier.padding(vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = IndustrialOrange,
                        selectedTextColor = IndustrialOrange
                    )
                )
            }
        }
    }
}
