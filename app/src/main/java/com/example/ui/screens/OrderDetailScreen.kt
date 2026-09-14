package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.data.model.OrderItemSnapshot
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentStatus
import com.example.data.model.ProductEntity
import com.example.ui.AnuToolsViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    orderId: String,
    viewModel: AnuToolsViewModel,
    onBack: () -> Unit
) {
    val order by viewModel.repository.getOrder(orderId).collectAsState(initial = null)
    val products by viewModel.products.collectAsState(initial = emptyList())

    var showPaymentDialog by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var selectedNewStatus by remember { mutableStateOf<OrderStatus?>(null) }
    var statusNote by remember { mutableStateOf("") }

    var paymentRefText by remember { mutableStateOf("") }
    var paymentNoteText by remember { mutableStateOf("") }
    var paymentAmountText by remember { mutableStateOf("") }

    var internalNoteText by remember { mutableStateOf("") }
    var isEditingNotes by remember { mutableStateOf(false) }

    if (order == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = IndustrialOrange)
        }
        return
    }

    val o = order!!

    LaunchedEffect(o) {
        internalNoteText = o.internalNote
        paymentRefText = o.paymentReference
        paymentAmountText = o.total.toString()
    }

    val itemsList = remember(o.itemsJson) {
        val list = mutableListOf<OrderItemSnapshot>()
        try {
            val arr = JSONArray(o.itemsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val rawImg = obj.optString("imageUrl").ifBlank {
                    obj.optString("thumbnailUrl").ifBlank {
                        obj.optString("image").ifBlank {
                            obj.optString("thumbnail", "")
                        }
                    }
                }
                val img = if (rawImg.trim().startsWith("data:", ignoreCase = true)) "" else rawImg.trim()
                list.add(
                    OrderItemSnapshot(
                        productId = obj.optString("productId"),
                        sku = obj.optString("sku"),
                        name = obj.optString("name"),
                        imageUrl = img,
                        quantity = obj.optInt("quantity", 1),
                        unit = obj.optString("unit", "Piece"),
                        unitPrice = obj.optDouble("unitPrice", 0.0),
                        mrp = obj.optDouble("mrp", 0.0),
                        gstPercentage = obj.optDouble("gstPercentage", 18.0),
                        gstAmount = obj.optDouble("gstAmount", 0.0),
                        subtotal = obj.optDouble("subtotal", 0.0)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    val historyEntries = remember(o.statusHistoryJson) {
        val list = mutableListOf<Triple<String, Long, String>>()
        try {
            val arr = JSONArray(o.statusHistoryJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val status = obj.optString("status")
                val time = obj.optLong("changedAt")
                val by = obj.optString("changedBy")
                val notes = obj.optString("notes")
                val desc = if (notes.isNotBlank()) "$by • $notes" else by
                list.add(Triple(status, time, desc))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(o.orderNumber, fontWeight = FontWeight.Bold)
                        Text(
                            text = formatDateTime(o.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    OrderStatusBadge(status = o.orderStatus)
                    Spacer(modifier = Modifier.width(12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Contextual Action Button based on order status
                    when (o.orderStatus) {
                        OrderStatus.PENDING -> {
                            Button(
                                onClick = {
                                    viewModel.updateOrderStatus(
                                        o.orderId,
                                        OrderStatus.CONFIRMED,
                                        "Order confirmed. Inventory deducted."
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Confirm Order (Deduct Stock)", fontWeight = FontWeight.Bold)
                            }
                        }
                        OrderStatus.CONFIRMED -> {
                            Button(
                                onClick = {
                                    viewModel.updateOrderStatus(
                                        o.orderId,
                                        OrderStatus.PACKING,
                                        "Items sent to warehouse packing department."
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusPurple),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Move to Packing", fontWeight = FontWeight.Bold)
                            }
                        }
                        OrderStatus.PACKING -> {
                            Button(
                                onClick = {
                                    viewModel.updateOrderStatus(
                                        o.orderId,
                                        OrderStatus.READY_FOR_DISPATCH,
                                        "Packed in cartons, awaiting transporter loading."
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mark Ready for Dispatch", fontWeight = FontWeight.Bold)
                            }
                        }
                        OrderStatus.READY_FOR_DISPATCH -> {
                            Button(
                                onClick = {
                                    viewModel.updateOrderStatus(
                                        o.orderId,
                                        OrderStatus.OUT_FOR_DELIVERY,
                                        "Handed over to transport / local vehicle delivery."
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = IndustrialOrange),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Dispatch / Out for Delivery", fontWeight = FontWeight.Bold)
                            }
                        }
                        OrderStatus.OUT_FOR_DELIVERY -> {
                            Button(
                                onClick = {
                                    viewModel.updateOrderStatus(
                                        o.orderId,
                                        OrderStatus.DELIVERED,
                                        "Order received by retailer. Delivery verified."
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mark Delivered", fontWeight = FontWeight.Bold)
                            }
                        }
                        OrderStatus.DELIVERED -> {
                            Surface(
                                color = StatusGreenBg,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreen)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Order Completed & Delivered", color = StatusGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        OrderStatus.CANCELLED -> {
                            Surface(
                                color = StatusRedBg,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Cancel, contentDescription = null, tint = StatusRed)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Order Cancelled (Stock Restored)", color = StatusRed, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Secondary manual status change button
                    OutlinedButton(
                        onClick = { showStatusDialog = true },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("More")
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Retailer Contact Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RETAILER / DELIVERY DESTINATION",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = IndustrialOrange)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = o.shopName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Contact: ${o.retailerName} (${o.phone})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (o.alternatePhone.isNotBlank()) {
                            Text(
                                text = "Alternate: ${o.alternatePhone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${o.address}, ${o.city} - ${o.pincode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Items Snapshot Table
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "ORDER ITEMS (${itemsList.size} PRODUCTS)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        itemsList.forEach { item ->
                            val matchingProduct = remember(products, item.productId, item.sku) {
                                products.firstOrNull { prod ->
                                    (item.productId.isNotBlank() && prod.productId == item.productId) ||
                                    (item.sku.isNotBlank() && prod.sku.equals(item.sku, ignoreCase = true)) ||
                                    (prod.name.equals(item.name, ignoreCase = true))
                                }
                            }
                            val displayImageUrl = remember(item.imageUrl, matchingProduct) {
                                resolveOrderItemImageUrl(item.imageUrl, matchingProduct)
                            }

                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Item image using imageUrl (Cloudinary HTTPS) with product fallback & placeholder
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (displayImageUrl != null) {
                                            SubcomposeAsyncImage(
                                                model = displayImageUrl,
                                                contentDescription = item.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                                loading = {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(16.dp),
                                                            strokeWidth = 2.dp,
                                                            color = IndustrialOrange
                                                        )
                                                    }
                                                },
                                                error = {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Hardware,
                                                            contentDescription = null,
                                                            tint = IndustrialOrange.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                }
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Hardware,
                                                contentDescription = null,
                                                tint = IndustrialOrange,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = "SKU: ${item.sku} • ${item.quantity} ${item.unit}(s) @ ${formatCurrency(item.unitPrice)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatCurrency(item.subtotal),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "+GST: ${formatCurrency(item.gstAmount)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        // Summary breakdown
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal", style = MaterialTheme.typography.bodyMedium)
                            Text(formatCurrency(o.subtotal), style = MaterialTheme.typography.bodyMedium)
                        }
                        if (o.discount > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Discount", style = MaterialTheme.typography.bodyMedium, color = StatusGreen)
                                Text("-${formatCurrency(o.discount)}", style = MaterialTheme.typography.bodyMedium, color = StatusGreen)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("GST (Tax)", style = MaterialTheme.typography.bodyMedium)
                            Text(formatCurrency(o.gst), style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Delivery Fee", style = MaterialTheme.typography.bodyMedium)
                            Text(if (o.deliveryCharge > 0) formatCurrency(o.deliveryCharge) else "Free", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Grand Total", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                formatCurrency(o.total),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = IndustrialOrange)
                            )
                        }
                    }
                }
            }

            // Payment Verification Section
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PAYMENT & VERIFICATION",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            PaymentStatusBadge(status = o.paymentStatus)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Method: ${o.paymentMethod.displayName}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Payment Amount: ${formatCurrency(if (o.amountPaid > 0) o.amountPaid else if (o.paymentStatus == PaymentStatus.PAID) o.total else 0.0)}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (o.paymentStatus == PaymentStatus.PAID) StatusGreen else MaterialTheme.colorScheme.onSurface
                        )
                        if (o.paymentReference.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "UPI ID / Transaction Ref: ${o.paymentReference}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = IndustrialOrange
                            )
                        }
                        if (o.paymentNote.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Verification Notes: ${o.paymentNote}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (o.paymentVerifiedBy != null && o.paymentVerifiedBy.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Verified by: ${o.paymentVerifiedBy} (${formatDateTime(o.paymentVerifiedAt ?: o.updatedAt)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusGreen
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        if (o.paymentStatus != PaymentStatus.PAID) {
                            Button(
                                onClick = {
                                    viewModel.updatePaymentStatus(
                                        orderId = o.orderId,
                                        newStatus = PaymentStatus.PAID,
                                        amount = o.total,
                                        ref = o.paymentReference,
                                        note = "Verified by Admin"
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Verify Payment (Mark Paid)", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        OutlinedButton(
                            onClick = { showPaymentDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Payment / Reference Notes")
                        }
                    }
                }
            }

            // Operational Notes Section
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "OPERATIONAL NOTES",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = { isEditingNotes = !isEditingNotes }) {
                                Icon(if (isEditingNotes) Icons.Default.Done else Icons.Default.Edit, contentDescription = "Edit Note")
                            }
                        }

                        if (o.customerNote.isNotBlank()) {
                            Text(
                                text = "Retailer Special Instructions:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = o.customerNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Text(
                            text = "Internal Admin / Dispatch Notes:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isEditingNotes) {
                            OutlinedTextField(
                                value = internalNoteText,
                                onValueChange = { internalNoteText = it },
                                placeholder = { Text("e.g. Godown location, driver contact, carton details...") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    viewModel.updateOrderNotes(o.orderId, internalNoteText, o.customerNote)
                                    isEditingNotes = false
                                }
                            ) {
                                Text("Save Note")
                            }
                        } else {
                            Text(
                                text = if (o.internalNote.isNotBlank()) o.internalNote else "No internal notes recorded.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Lifecycle Audit History
            item {
                Text(
                    text = "STATUS AUDIT TIMELINE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(historyEntries) { (statusStr, time, desc) ->
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(IndustrialOrange)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = statusStr,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = IndustrialOrange
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = formatDateTime(time),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Payment Verification Dialog
    if (showPaymentDialog) {
        var selectedPayStatus by remember { mutableStateOf(o.paymentStatus) }
        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            title = { Text("Update Payment Status") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Total Order Amount: ${formatCurrency(o.total)}", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = paymentRefText,
                        onValueChange = { paymentRefText = it },
                        label = { Text("Transaction ID / UPI UTR / Receipt Ref") },
                        placeholder = { Text("e.g. UPI/429810238491/GPay") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = paymentAmountText,
                        onValueChange = { paymentAmountText = it },
                        label = { Text("Amount Verified (₹)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = paymentNoteText,
                        onValueChange = { paymentNoteText = it },
                        label = { Text("Verification Notes") },
                        placeholder = { Text("e.g. Bank statement matched") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedPayStatus == PaymentStatus.PAID,
                            onClick = { selectedPayStatus = PaymentStatus.PAID },
                            label = { Text("Paid") }
                        )
                        FilterChip(
                            selected = selectedPayStatus == PaymentStatus.PAYMENT_PENDING_VERIFICATION,
                            onClick = { selectedPayStatus = PaymentStatus.PAYMENT_PENDING_VERIFICATION },
                            label = { Text("Pending") }
                        )
                        FilterChip(
                            selected = selectedPayStatus == PaymentStatus.UNPAID,
                            onClick = { selectedPayStatus = PaymentStatus.UNPAID },
                            label = { Text("Unpaid") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = paymentAmountText.toDoubleOrNull() ?: o.total
                        viewModel.updatePaymentStatus(
                            orderId = o.orderId,
                            newStatus = selectedPayStatus,
                            amount = amt,
                            ref = paymentRefText,
                            note = paymentNoteText
                        )
                        showPaymentDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndustrialOrange)
                ) {
                    Text("Confirm Payment")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaymentDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Manual Status Change Dialog
    if (showStatusDialog) {
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            title = { Text("Change Order Status") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select target status for ${o.orderNumber}:")
                    OrderStatus.values().forEach { st ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = (selectedNewStatus ?: o.orderStatus) == st,
                                onClick = { selectedNewStatus = st }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(st.displayName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    OutlinedTextField(
                        value = statusNote,
                        onValueChange = { statusNote = it },
                        label = { Text("Status change note") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = selectedNewStatus ?: o.orderStatus
                        viewModel.updateOrderStatus(o.orderId, target, statusNote)
                        showStatusDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndustrialOrange)
                ) {
                    Text("Update Status")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStatusDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Resolves a valid HTTPS/HTTP image URL for an order item snapshot.
 * 1. Prioritizes item.imageUrl (e.g. Cloudinary HTTPS URL).
 * 2. Falls back to matching product thumbnail or first product image if imageUrl is missing.
 * 3. Strictly blocks and ignores Base64 data (e.g. data: URLs).
 * 4. Returns null if no valid HTTP/HTTPS URL exists, triggering the placeholder.
 */
internal fun resolveOrderItemImageUrl(itemImageUrl: String?, product: ProductEntity?): String? {
    fun isValidHttpUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        if (trimmed.startsWith("data:", ignoreCase = true)) return false
        return trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)
    }

    if (isValidHttpUrl(itemImageUrl)) {
        return itemImageUrl!!.trim()
    }
    if (product != null) {
        if (isValidHttpUrl(product.thumbnailUrl)) {
            return product.thumbnailUrl.trim()
        }
        val firstValidImage = product.getImagesList().firstOrNull { isValidHttpUrl(it) }
        if (firstValidImage != null) {
            return firstValidImage.trim()
        }
    }
    return null
}

