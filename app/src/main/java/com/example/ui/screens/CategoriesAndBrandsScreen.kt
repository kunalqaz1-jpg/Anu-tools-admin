package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BrandEntity
import com.example.data.model.CategoryEntity
import com.example.ui.AnuToolsViewModel
import com.example.ui.components.*
import com.example.ui.theme.IndustrialOrange
import java.util.UUID

@Composable
fun CategoriesAndBrandsScreen(viewModel: AnuToolsViewModel) {
    val categories by viewModel.categories.collectAsState(initial = emptyList())
    val brands by viewModel.brands.collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(0) } // 0: Categories, 1: Brands

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    var showAddBrandDialog by remember { mutableStateOf(false) }
    var editingBrand by remember { mutableStateOf<BrandEntity?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Categories & Brands",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Organize Anu Tools machinery and consumables hierarchy",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = IndustrialOrange
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Categories (${categories.size})", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Brands (${brands.size})", fontWeight = FontWeight.Bold) }
                    )
                }
            }

            HorizontalDivider()

            if (selectedTab == 0) {
                // Categories List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(categories, key = { it.categoryId }) { cat ->
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
                                        text = cat.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    if (cat.description.isNotBlank()) {
                                        Text(
                                            text = cat.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "Slug: ${cat.slug} • Order: ${cat.sortOrder}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(onClick = { editingCategory = cat }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                                    }
                                    IconButton(onClick = { viewModel.deleteCategory(cat) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Brands List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(brands, key = { it.brandId }) { brand ->
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
                                        text = brand.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    if (brand.description.isNotBlank()) {
                                        Text(
                                            text = brand.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "Slug: ${brand.slug} • Order: ${brand.sortOrder}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(onClick = { editingBrand = brand }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                                    }
                                    IconButton(onClick = { viewModel.deleteBrand(brand) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                if (selectedTab == 0) showAddCategoryDialog = true else showAddBrandDialog = true
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (selectedTab == 0) "Add Category" else "Add Brand", fontWeight = FontWeight.Bold)
            }
        }
    }

    // Add / Edit Category Dialog
    if (showAddCategoryDialog || editingCategory != null) {
        val existing = editingCategory
        var catName by remember { mutableStateOf(existing?.name ?: "") }
        var catDesc by remember { mutableStateOf(existing?.description ?: "") }
        var sortOrderText by remember { mutableStateOf(existing?.sortOrder?.toString() ?: "1") }

        AlertDialog(
            onDismissRequest = {
                showAddCategoryDialog = false
                editingCategory = null
            },
            title = { Text(if (existing != null) "Edit Category" else "Add Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = catName,
                        onValueChange = { catName = it },
                        label = { Text("Category Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = catDesc,
                        onValueChange = { catDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = sortOrderText,
                        onValueChange = { sortOrderText = it },
                        label = { Text("Sort Order") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (catName.isBlank()) return@Button
                        val order = sortOrderText.toIntOrNull() ?: 1
                        val cat = (existing ?: CategoryEntity(
                            categoryId = "cat_${UUID.randomUUID().toString().take(6)}",
                            name = catName,
                            slug = catName.lowercase().replace(" ", "-"),
                            sortOrder = order,
                            description = catDesc
                        )).copy(name = catName, description = catDesc, sortOrder = order)
                        viewModel.saveCategory(cat)
                        showAddCategoryDialog = false
                        editingCategory = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndustrialOrange)
                ) {
                    Text("Save Category")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddCategoryDialog = false
                    editingCategory = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add / Edit Brand Dialog
    if (showAddBrandDialog || editingBrand != null) {
        val existing = editingBrand
        var brandName by remember { mutableStateOf(existing?.name ?: "") }
        var brandDesc by remember { mutableStateOf(existing?.description ?: "") }
        var sortOrderText by remember { mutableStateOf(existing?.sortOrder?.toString() ?: "1") }

        AlertDialog(
            onDismissRequest = {
                showAddBrandDialog = false
                editingBrand = null
            },
            title = { Text(if (existing != null) "Edit Brand" else "Add Brand") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = brandName,
                        onValueChange = { brandName = it },
                        label = { Text("Brand Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = brandDesc,
                        onValueChange = { brandDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = sortOrderText,
                        onValueChange = { sortOrderText = it },
                        label = { Text("Sort Order") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (brandName.isBlank()) return@Button
                        val order = sortOrderText.toIntOrNull() ?: 1
                        val br = (existing ?: BrandEntity(
                            brandId = "br_${UUID.randomUUID().toString().take(6)}",
                            name = brandName,
                            slug = brandName.lowercase().replace(" ", "-"),
                            sortOrder = order,
                            description = brandDesc
                        )).copy(name = brandName, description = brandDesc, sortOrder = order)
                        viewModel.saveBrand(br)
                        showAddBrandDialog = false
                        editingBrand = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndustrialOrange)
                ) {
                    Text("Save Brand")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddBrandDialog = false
                    editingBrand = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
