package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.AdminNavDestination
import com.example.ui.AnuToolsViewModel
import com.example.ui.components.AdminBottomNav
import com.example.ui.components.AdminNavigationDrawerContent
import com.example.ui.components.AdminTopBar
import com.example.ui.screens.*
import com.example.ui.theme.IndustrialOrange
import com.example.ui.theme.MyApplicationTheme
import com.example.util.DataUriFetcher
import coil.Coil
import coil.ImageLoader
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: AnuToolsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure global Coil ImageLoader to natively decode data: URIs (base64 images)
        // as well as standard HTTP/HTTPS/Content URIs.
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(DataUriFetcher.Factory())
                add(DataUriFetcher.UriFactory())
            }
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AnuToolsAdminApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnuToolsAdminApp(viewModel: AnuToolsViewModel) {
    val authState by viewModel.authState.collectAsState()
    val currentNav by viewModel.currentNav.collectAsState()
    val selectedProductId by viewModel.selectedProductId.collectAsState()
    val selectedOrderId by viewModel.selectedOrderId.collectAsState()
    val selectedRetailerId by viewModel.selectedRetailerId.collectAsState()
    val backStack by viewModel.backStack.collectAsState()
    val toastMsg by viewModel.toastMessage.collectAsState()
    val context = LocalContext.current

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var showMoreSheet by remember { mutableStateOf(false) }

    // System Back Button Navigation Handling
    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }

    BackHandler(enabled = showMoreSheet && !drawerState.isOpen) {
        showMoreSheet = false
    }

    val canGoBack = backStack.size > 1
    BackHandler(enabled = canGoBack && !drawerState.isOpen && !showMoreSheet) {
        viewModel.goBack()
    }

    LaunchedEffect(toastMsg) {
        toastMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    if (!authState.isAuthenticated) {
        AuthScreen(viewModel = viewModel)
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AdminNavigationDrawerContent(
                currentNav = currentNav,
                onSelectDestination = { dest ->
                    viewModel.navigateTo(dest)
                },
                onCloseDrawer = {
                    coroutineScope.launch { drawerState.close() }
                },
                currentUser = authState.currentUser
            )
        }
    ) {
        Scaffold(
            topBar = {
                // Show standard TopBar only if not in detail views (which have their own back topbars)
                if (selectedProductId == null && selectedOrderId == null && selectedRetailerId == null) {
                    AdminTopBar(
                        viewModel = viewModel,
                        onMenuClick = {
                            coroutineScope.launch { drawerState.open() }
                        }
                    )
                }
            },
            bottomBar = {
                if (selectedProductId == null && selectedOrderId == null && selectedRetailerId == null) {
                    AdminBottomNav(
                        currentNav = currentNav,
                        onNavigate = { dest -> viewModel.navigateTo(dest) },
                        onOpenMoreMenu = { showMoreSheet = true }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 900.dp)
                ) {
                    when {
                        selectedProductId != null -> {
                            ProductDetailScreen(
                                productId = selectedProductId!!,
                                viewModel = viewModel,
                                onBack = { viewModel.closeProductDetails() }
                            )
                        }
                        selectedOrderId != null -> {
                            OrderDetailScreen(
                                orderId = selectedOrderId!!,
                                viewModel = viewModel,
                                onBack = { viewModel.closeOrderDetails() }
                            )
                        }
                        selectedRetailerId != null -> {
                            RetailerDetailScreen(
                                retailerId = selectedRetailerId!!,
                                viewModel = viewModel,
                                onBack = { viewModel.closeRetailerDetails() },
                                onOpenOrderDetail = { orderId -> viewModel.openOrderDetails(orderId) }
                            )
                        }
                        else -> {
                            when (currentNav) {
                                AdminNavDestination.DASHBOARD -> DashboardScreen(
                                    viewModel = viewModel,
                                    onNavigate = { dest -> viewModel.navigateTo(dest) }
                                )
                                AdminNavDestination.PRODUCTS -> ProductsScreen(
                                    viewModel = viewModel,
                                    onOpenProductDetail = { id -> viewModel.openProductDetails(id) }
                                )
                                AdminNavDestination.ORDERS -> OrdersScreen(
                                    viewModel = viewModel,
                                    onOpenOrderDetail = { id -> viewModel.openOrderDetails(id) }
                                )
                                AdminNavDestination.INVENTORY -> InventoryScreen(
                                    viewModel = viewModel,
                                    onOpenProductDetail = { id -> viewModel.openProductDetails(id) }
                                )
                                AdminNavDestination.CATEGORIES_BRANDS -> CategoriesAndBrandsScreen(
                                    viewModel = viewModel
                                )
                                AdminNavDestination.RETAILERS -> RetailersScreen(
                                    viewModel = viewModel
                                )
                                AdminNavDestination.REPORTS -> ReportsScreen(
                                    viewModel = viewModel
                                )
                                AdminNavDestination.NOTIFICATIONS -> NotificationsScreen(
                                    viewModel = viewModel,
                                    onNavigate = { dest -> viewModel.navigateTo(dest) }
                                )
                                AdminNavDestination.SETTINGS -> SettingsScreen(
                                    viewModel = viewModel
                                )
                                AdminNavDestination.AUDIT_LOG -> AuditLogScreen(
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // More Sheet Bottom Sheet
    if (showMoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "MORE BUSINESS MODULES",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                val moreOptions = listOf(
                    Triple(AdminNavDestination.CATEGORIES_BRANDS, Icons.Default.Category, "Categories & Brands"),
                    Triple(AdminNavDestination.RETAILERS, Icons.Default.Storefront, "Retailer Accounts"),
                    Triple(AdminNavDestination.REPORTS, Icons.Default.BarChart, "Sales & Reports"),
                    Triple(AdminNavDestination.NOTIFICATIONS, Icons.Default.Notifications, "Notifications"),
                    Triple(AdminNavDestination.AUDIT_LOG, Icons.Default.History, "Audit Trail"),
                    Triple(AdminNavDestination.SETTINGS, Icons.Default.Settings, "Business Settings")
                )

                moreOptions.forEach { (dest, icon, label) ->
                    Surface(
                        onClick = {
                            viewModel.navigateTo(dest)
                            showMoreSheet = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = null, tint = IndustrialOrange)
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
