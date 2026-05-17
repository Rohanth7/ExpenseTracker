package com.example.expensetracker.ui.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.ui.theme.*
import com.example.expensetracker.ui.util.parseColor
import java.text.NumberFormat
import java.util.Locale

private val PRESET_EMOJIS = listOf(
    "🍔", "🛒", "🚗", "🏠", "💊", "🎮", "📚", "✈️",
    "💡", "👗", "💰", "🎵", "🍺", "☕", "🎁", "💼"
)

private val PRESET_COLORS = listOf(
    "#F44336", "#E91E63", "#9C27B0", "#673AB7",
    "#3F51B5", "#2196F3", "#00BCD4", "#009688",
    "#4CAF50", "#8BC34A", "#FFEB3B", "#FF9800"
)

private fun fmtINR(amount: Double): String =
    NumberFormat.getNumberInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }.format(amount)

@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel) {
    val categories by viewModel.categories.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Canvas)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 14.dp)) {
                Text(
                    text = "BUCKETS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    letterSpacing = 1.8.sp,
                    color = Muted
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "Categories",
                        fontFamily = FontFamily.Serif,
                        fontSize = 28.sp,
                        fontStyle = FontStyle.Italic,
                        color = Ink,
                        lineHeight = 30.sp
                    )
                    Text(
                        text = "${categories.size} active",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Muted,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 90.dp)
            ) {
                // Categories list card
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Paper)
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        if (categories.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No categories yet.\nTap + to create one.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = Muted,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp
                                )
                            }
                        } else {
                            Column {
                                categories.forEachIndexed { index, cat ->
                                    if (index > 0) {
                                        HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                                    }
                                    CategoryRow(
                                        category = cat,
                                        onEdit = { editTarget = cat; showDialog = true },
                                        onDelete = { categoryToDelete = cat }
                                    )
                                }
                            }
                        }
                    }
                }

                // Available palette section label
                item {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "AVAILABLE PALETTE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.6.sp,
                        color = Muted,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                // Palette card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Paper)
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "Each new category picks an icon and a color.",
                            fontSize = 12.sp,
                            color = Muted,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PRESET_COLORS.forEach { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(parseColor(hex))
                                )
                            }
                        }
                        HorizontalDivider(
                            color = HairlineSoft,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PRESET_EMOJIS.forEach { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Canvas),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 17.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // FAB
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 92.dp)
                .size(58.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Jade)
                .clickable { editTarget = null; showDialog = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add category",
                tint = Deep,
                modifier = Modifier.size(26.dp)
            )
        }
    }

    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            containerColor = Paper,
            titleContentColor = Ink,
            textContentColor = Muted,
            title = { Text("Delete Category") },
            text = { Text("Delete \"${cat.name}\"? All its expenses will be moved back to pending.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteCategory(cat); categoryToDelete = null }) {
                    Text("Delete", color = Coral)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) { Text("Cancel", color = Muted) }
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
private fun CategoryRow(category: Category, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(parseColor(category.colorHex)),
            contentAlignment = Alignment.Center
        ) {
            Text(category.emoji, fontSize = 20.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                if (category.monthlyLimit > 0) {
                    Text(
                        text = "limit ₹${fmtINR(category.monthlyLimit)}/mo",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        color = Muted
                    )
                } else {
                    Text(
                        text = "no limit set",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        color = Muted.copy(alpha = 0.7f)
                    )
                }
            }
        }
        Row {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Muted, modifier = Modifier.size(16.dp))
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Muted, modifier = Modifier.size(16.dp))
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
    var limitText by remember {
        mutableStateOf(if ((initial?.monthlyLimit ?: 0.0) > 0) "%.0f".format(initial?.monthlyLimit) else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        titleContentColor = Ink,
        textContentColor = Muted,
        title = { Text(if (initial == null) "New Category" else "Edit Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Jade,
                        focusedLabelColor = Jade,
                        unfocusedBorderColor = Hairline,
                        unfocusedLabelColor = Muted,
                        focusedTextColor = Ink,
                        unfocusedTextColor = Ink,
                        cursorColor = Jade
                    )
                )
                Text("Icon", style = MaterialTheme.typography.labelMedium, color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PRESET_EMOJIS.forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (emoji == selectedEmoji) JadeDeep else Color.Transparent)
                                .clickable { selectedEmoji = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 18.sp)
                        }
                    }
                }
                Text("Color", style = MaterialTheme.typography.labelMedium, color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PRESET_COLORS.forEach { hex ->
                        val selected = hex == selectedColor
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(parseColor(hex))
                                .then(if (selected) Modifier.border(2.dp, Ink, CircleShape) else Modifier)
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
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Jade,
                        focusedLabelColor = Jade,
                        unfocusedBorderColor = Hairline,
                        unfocusedLabelColor = Muted,
                        focusedTextColor = Ink,
                        unfocusedTextColor = Ink,
                        cursorColor = Jade
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                onConfirm(name.trim(), selectedEmoji, selectedColor, limitText.toDoubleOrNull() ?: 0.0)
            }) { Text("Save", color = Jade) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
}
