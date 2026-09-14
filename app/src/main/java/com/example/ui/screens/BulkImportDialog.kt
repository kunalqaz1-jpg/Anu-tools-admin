package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.ProductEntity
import com.example.ui.AnuToolsViewModel
import com.example.ui.theme.*
import com.example.util.CsvRowValidation
import com.example.util.ImageModelResolver
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkImportDialog(
    viewModel: AnuToolsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val importState by viewModel.bulkImportState.collectAsState()

    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedPreviewTab by remember { mutableIntStateOf(0) } // 0 = Valid, 1 = Invalid, 2 = All

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "selected.csv"
            viewModel.analyzeCsvFromUri(uri, context)
        }
    }

    // Document creator for template download
    val saveTemplateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(viewModel.getSampleCsvTemplate().toByteArray())
                }
                Toast.makeText(context, "CSV template saved successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save template: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (!importState.isImporting) {
                viewModel.resetBulkImport()
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .testTag("bulk_import_dialog"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(IndustrialOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = null,
                                tint = IndustrialOrange,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Bulk Product Import",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Import product catalog from CSV file",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            viewModel.resetBulkImport()
                            onDismiss()
                        },
                        enabled = !importState.isImporting,
                        modifier = Modifier.testTag("close_import_dialog_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons: Primary Select CSV Button + Secondary Template Actions
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("select_csv_button"),
                        enabled = !importState.isImporting && !importState.isAnalyzing,
                        colors = ButtonDefaults.buttonColors(containerColor = IndustrialOrange),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedFileName == null) "Select CSV File" else "Change CSV File",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                saveTemplateLauncher.launch("anu_tools_import_template.csv")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("download_template_button"),
                            enabled = !importState.isImporting,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download Template", maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                shareCsvTemplate(context, viewModel.getSampleCsvTemplate())
                            },
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("share_template_button"),
                            enabled = !importState.isImporting,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }

                if (selectedFileName != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Selected: $selectedFileName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Main Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        importState.isAnalyzing -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = IndustrialOrange)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Parsing and validating CSV...", fontWeight = FontWeight.Medium)
                                Text(
                                    "Checking required fields, non-negative values, and SKU duplicates",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        importState.isImporting -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = IndustrialOrange)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Importing products to Firestore & database...", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = IndustrialOrange
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Executing batched Firestore operations and updating stock ledger",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        importState.importResultSummary != null -> {
                            Card(
                                modifier = Modifier.fillMaxSize(),
                                colors = CardDefaults.cardColors(containerColor = StatusGreenBg),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = StatusGreen,
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Import Successful!",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = StatusGreen
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = importState.importResultSummary ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = {
                                            viewModel.resetBulkImport()
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("done_import_button")
                                    ) {
                                        Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        importState.analysis != null -> {
                            val analysis = importState.analysis!!
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Summary Badges Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MetricChip(
                                        title = "Total Rows",
                                        count = analysis.totalRows,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricChip(
                                        title = "Valid Rows",
                                        count = analysis.validCount,
                                        containerColor = StatusGreenBg,
                                        contentColor = StatusGreen,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricChip(
                                        title = "Invalid Rows",
                                        count = analysis.invalidCount,
                                        containerColor = StatusRedBg,
                                        contentColor = StatusRed,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricChip(
                                        title = "Duplicates",
                                        count = analysis.duplicateCount,
                                        containerColor = StatusAmberBg,
                                        contentColor = StatusAmber,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Filter Tabs (Valid, Invalid/Duplicates, All)
                                TabRow(
                                    selectedTabIndex = selectedPreviewTab,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Tab(
                                        selected = selectedPreviewTab == 0,
                                        onClick = { selectedPreviewTab = 0 },
                                        text = { Text("Valid (${analysis.validCount})") }
                                    )
                                    Tab(
                                        selected = selectedPreviewTab == 1,
                                        onClick = { selectedPreviewTab = 1 },
                                        text = { Text("Errors / Duplicates (${analysis.invalidCount})") }
                                    )
                                    Tab(
                                        selected = selectedPreviewTab == 2,
                                        onClick = { selectedPreviewTab = 2 },
                                        text = { Text("All Rows (${analysis.totalRows})") }
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // List Content
                                Box(modifier = Modifier.weight(1f)) {
                                    when (selectedPreviewTab) {
                                        0 -> {
                                            if (analysis.validRows.isEmpty()) {
                                                EmptyPreviewNotice("No valid products found in this CSV.")
                                            } else {
                                                LazyColumn(
                                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    items(analysis.validRows, key = { it.sku }) { prod ->
                                                        ValidProductPreviewItem(prod)
                                                    }
                                                }
                                            }
                                        }
                                        1 -> {
                                            if (analysis.invalidRows.isEmpty()) {
                                                EmptyPreviewNotice("No invalid rows! All rows passed validation.")
                                            } else {
                                                LazyColumn(
                                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    items(analysis.invalidRows, key = { "${it.rowNumber}_${it.rawSku}" }) { row ->
                                                        InvalidRowPreviewItem(row)
                                                    }
                                                }
                                            }
                                        }
                                        else -> {
                                            LazyColumn(
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                items(analysis.allRows, key = { "${it.rowNumber}_${it.rawSku}" }) { row ->
                                                    if (row.isValid && row.product != null) {
                                                        ValidProductPreviewItem(row.product)
                                                    } else {
                                                        InvalidRowPreviewItem(row)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            // Initial Empty State
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = IndustrialOrange,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No CSV File Loaded",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Click 'Select CSV File' above to upload your inventory spreadsheet, or download our ready-made CSV template.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Required CSV Columns:",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "name, sku, sellingPrice, retailerPrice, mrp, stockQuantity, gst, categoryName, brandName, minStock, unit, description, shortDescription, featured, active",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Error Banner if present
                if (importState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StatusRedBg),
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
                                text = importState.errorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusRed
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.resetBulkImport()
                            onDismiss()
                        },
                        enabled = !importState.isImporting,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel")
                    }

                    if (importState.analysis != null && importState.analysis!!.validCount > 0) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                viewModel.confirmBulkImport {
                                    // Successfully imported
                                }
                            },
                            enabled = !importState.isImporting,
                            colors = ButtonDefaults.buttonColors(containerColor = IndustrialOrange),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("confirm_import_button")
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Import ${importState.analysis!!.validCount} Product(s)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    title: String,
    count: Int,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = contentColor
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.85f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ValidProductPreviewItem(prod: ProductEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("preview_item_${prod.sku}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header Row: Image, Name, Badge, Price, Stock
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (prod.thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageModelResolver.resolveImageModel(prod.thumbnailUrl),
                            contentDescription = prod.name,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = prod.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(containerColor = StatusGreenBg, contentColor = StatusGreen) {
                                Text("VALID", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "SKU: ${prod.sku} • Brand: ${prod.brandName} • Category: ${prod.categoryName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${prod.sellingPrice}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Stock: ${prod.stockQuantity} ${prod.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (prod.stockQuantity > 0) StatusGreen else StatusRed,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Verified Fields Section: Name, Short Description, Description
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Field 1: Name -> Firestore "name"
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Name: ",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = IndustrialOrange
                        )
                        Text(
                            text = prod.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Field 2: Short Description -> Firestore "shortDescription"
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Short Desc: ",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = prod.shortDescription.ifBlank { "(None)" },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (prod.shortDescription.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Field 3: Description -> Firestore "description"
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Description: ",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = prod.description.ifBlank { "(None)" },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (prod.description.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Optional specs indicator
                    val specsText = remember(prod.specificationsJson) {
                        try {
                            val json = JSONObject(prod.specificationsJson)
                            val parts = mutableListOf<String>()
                            json.keys().forEach { k ->
                                parts.add("$k: ${json.optString(k)}")
                            }
                            parts.joinToString(" • ")
                        } catch (_: Exception) { "" }
                    }
                    if (specsText.isNotBlank()) {
                        Text(
                            text = "Specs: $specsText",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InvalidRowPreviewItem(row: CsvRowValidation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (row.isDuplicateInCsv || row.isDuplicateInCatalog) StatusAmberBg.copy(alpha = 0.4f) else StatusRedBg.copy(alpha = 0.4f)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Row ${row.rowNumber}: ${row.rawName.ifBlank { "Untitled Product" }}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val badgeText = when {
                    row.isDuplicateInCsv -> "CSV DUPLICATE"
                    row.isDuplicateInCatalog -> "DB CONFLICT"
                    else -> "ERROR"
                }
                val badgeColor = if (row.isDuplicateInCsv || row.isDuplicateInCatalog) StatusAmber else StatusRed
                val badgeBg = if (row.isDuplicateInCsv || row.isDuplicateInCatalog) StatusAmberBg else StatusRedBg

                Badge(containerColor = badgeBg, contentColor = badgeColor) {
                    Text(badgeText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (row.rawSku.isNotBlank()) {
                Text(
                    text = "SKU: ${row.rawSku}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            if (row.rawShortDescription.isNotBlank()) {
                Text(
                    text = "Short Desc: ${row.rawShortDescription}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // List of specific errors
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                row.errors.forEach { err ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = StatusRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusRed,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPreviewNotice(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun shareCsvTemplate(context: Context, csvContent: String) {
    try {
        val cacheFile = File(context.cacheDir, "anu_tools_product_import_template.csv")
        FileOutputStream(cacheFile).use { os ->
            os.write(csvContent.toByteArray())
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Anu Tools Bulk Import Template")
            putExtra(Intent.EXTRA_TEXT, csvContent)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share CSV Template"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share CSV: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
