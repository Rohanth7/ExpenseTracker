package com.example.expensetracker.ui.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.ui.util.parseColor

private val PRESET_EMOJIS = listOf(
    "🍔", "🛒", "🚗", "🏠", "💊", "🎮", "📚", "✈️",
    "💡", "👗", "💰", "🎵", "🍺", "☕", "🎁", "💼"
)

private val PRESET_COLORS = listOf(
    "#F44336", "#E91E63", "#9C27B0", "#673AB7",
    "#3F51B5", "#2196F3", "#00BCD4", "#009688",
    "#4CAF50", "#8BC34A", "#FFEB3B", "#FF9800"
)

@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel) {
    val categories by viewModel.categories.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editTarget = null; showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add category")
            }
        }
    ) { padding ->
        if (categories.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No categories yet.\nTap + to create one.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories, key = { it.id }) { cat ->
                    CategoryItem(
                        category = cat,
                        onEdit = { editTarget = cat; showDialog = true },
                        onDelete = { categoryToDelete = cat }
                    )
                }
            }
        }
    }

    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete Category") },
            text = {
                Text("Delete \"${cat.name}\"? All its expenses will be moved back to pending (uncategorized).")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(cat)
                    categoryToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showDialog) {
        CategoryDialog(
            initial = editTarget,
            onDismiss = { showDialog = false },
            onConfirm = { name, emoji, colorHex, limit ->
                val existing = editTarget
                if (existing != null) {
                    viewModel.updateCategory(existing.copy(name = name, emoji = emoji, colorHex = colorHex, monthlyLimit = limit))
                } else {
                    viewModel.addCategory(name, emoji, colorHex, limit)
                }
                showDialog = false
            }
        )
    }
}

@Composable
private fun CategoryItem(category: Category, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape)
                    .background(parseColor(category.colorHex)),
                contentAlignment = Alignment.Center
            ) {
                Text(category.emoji, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(category.name, fontWeight = FontWeight.SemiBold)
                if (category.monthlyLimit > 0) {
                    Text(
                        "Limit: ₹${"%.0f".format(category.monthlyLimit)}/month",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun CategoryDialog(
    initial: Category?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Double) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var selectedEmoji by remember { mutableStateOf(initial?.emoji ?: PRESET_EMOJIS.first()) }
    var selectedColor by remember { mutableStateOf(initial?.colorHex ?: PRESET_COLORS.first()) }
    var limitText by remember { mutableStateOf(if ((initial?.monthlyLimit ?: 0.0) > 0) "%.0f".format(initial?.monthlyLimit) else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Category" else "Edit Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Icon", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PRESET_EMOJIS.forEach { emoji ->
                        Box(
                            modifier = Modifier.size(36.dp)
                                .clip(CircleShape)
                                .background(if (emoji == selectedEmoji) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .border(1.dp, if (emoji == selectedEmoji) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                .clickable { selectedEmoji = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 18.sp)
                        }
                    }
                }
                Text("Color", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PRESET_COLORS.forEach { hex ->
                        Box(
                            modifier = Modifier.size(28.dp).clip(CircleShape)
                                .background(parseColor(hex))
                                .border(
                                    width = if (hex == selectedColor) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("Monthly limit ₹ (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                val limit = limitText.toDoubleOrNull() ?: 0.0
                onConfirm(name.trim(), selectedEmoji, selectedColor, limit)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
