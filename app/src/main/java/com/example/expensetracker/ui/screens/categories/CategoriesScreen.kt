package com.example.expensetracker.ui.screens.categories

import com.example.expensetracker.ui.util.MAX_CATEGORY_NAME_LENGTH
import com.example.expensetracker.ui.util.categoryNameError
import com.example.expensetracker.ui.util.fmtINR
import com.example.expensetracker.ui.util.rememberSaveableItem
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.ui.theme.*
import com.example.expensetracker.ui.util.formatLimitInput
import com.example.expensetracker.ui.util.parseColor
import com.example.expensetracker.ui.util.parseLimitInput
import com.example.expensetracker.ui.util.rememberAmountText
import com.example.expensetracker.ui.components.*
import com.example.expensetracker.ui.screens.expenses.Tags

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
fun CategoriesScreen(viewModel: CategoriesViewModel, onBack: () -> Unit = {}) {
    val categories by viewModel.categories.collectAsState()
    val groupedCategories by viewModel.groupedCategories.collectAsState()
    val tagSummaries by viewModel.allTagSummaries.collectAsState()
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var editTarget by rememberSaveableItem(categories) { it.id }
    var editingExisting by rememberSaveable { mutableStateOf(false) }
    var categoryToDelete by rememberSaveableItem(categories) { it.id }
    var addSubcategoryFor by rememberSaveableItem(categories) { it.id }
    var tagToRename by rememberSaveable { mutableStateOf<String?>(null) }
    var tagToDelete by rememberSaveable { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Canvas)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 96.dp)
        ) {
            item {
                ScreenHeader("Categories & tags", onBack = onBack, actions = {
                    val subCount = categories.count { it.parentId != null }
                    Text(
                        text = if (subCount > 0) "${categories.count { it.parentId == null }} + $subCount sub" else "${categories.size} active",
                        fontSize = 13.sp,
                        color = Muted
                    )
                })
            }

            item {
                MCard(padding = RowCardPadding) {
                    if (groupedCategories.isEmpty()) {
                        Text(
                            text = "No categories yet.\nTap + to create one.",
                            fontSize = 13.sp,
                            color = Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp)
                        )
                    } else {
                        groupedCategories.forEachIndexed { groupIndex, group ->
                            if (groupIndex > 0) RowDivider()
                            CategoryRow(
                                category = group.parent,
                                onEdit = { editTarget = group.parent; editingExisting = true; showDialog = true },
                                onDelete = { categoryToDelete = group.parent },
                                onAddSubcategory = { addSubcategoryFor = group.parent }
                            )
                            group.children.forEach { child ->
                                RowDivider()
                                SubcategoryRow(
                                    category = child,
                                    onEdit = { editTarget = child; editingExisting = true; showDialog = true },
                                    onDelete = { categoryToDelete = child }
                                )
                            }
                        }
                    }
                }
            }

            if (tagSummaries.isNotEmpty()) {
                item {
                    SectionLabel("Tags")
                    MCard(padding = RowCardPadding) {
                        tagSummaries.forEachIndexed { index, tag ->
                            if (index > 0) RowDivider()
                            AccentRow(
                                accent = Gold,
                                title = "#${tag.name}",
                                subtitle = "${tag.count} expense${if (tag.count != 1) "s" else ""}",
                                trailing = "₹${fmtINR(tag.totalSpent)}",
                                actions = {
                                    Spacer(Modifier.width(4.dp))
                                    RowAction(Icons.Default.Edit, "Rename tag") { tagToRename = tag.name }
                                    RowAction(Icons.Default.Delete, "Delete tag") { tagToDelete = tag.name }
                                }
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = ScreenPadding, bottom = 24.dp)
                .size(56.dp)
                .clip(ButtonShape)
                .background(Gold)
                .clickable { editTarget = null; editingExisting = false; showDialog = true },
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
        val hasChildren = groupedCategories.find { it.parent.id == cat.id }?.children?.isNotEmpty() == true
        val deleteMsg = when {
            hasChildren -> "Delete \"${cat.name}\" and all its subcategories? All expenses will be moved to pending."
            cat.parentId != null -> "Delete subcategory \"${cat.name}\"? Its expenses will be moved to pending."
            else -> "Delete \"${cat.name}\"? All its expenses will be moved back to pending."
        }
        AppAlertDialog(
            onDismissRequest = { categoryToDelete = null },
            containerColor = Paper,
            titleContentColor = Ink,
            textContentColor = Muted,
            title = { Text("Delete Category") },
            text = { Text(deleteMsg) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteCategory(cat); categoryToDelete = null }) {
                    Text("Delete", color = Pink)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) { Text("Cancel", color = Muted) }
            }
        )
    }

    tagToDelete?.let { name ->
        AppAlertDialog(
            onDismissRequest = { tagToDelete = null },
            containerColor = Paper,
            titleContentColor = Ink,
            textContentColor = Muted,
            title = { Text("Delete tag?") },
            text = { Text("Remove \"#$name\" from all expenses?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteTag(name); tagToDelete = null }) {
                    Text("Delete", color = Pink)
                }
            },
            dismissButton = {
                TextButton(onClick = { tagToDelete = null }) { Text("Cancel", color = Muted) }
            }
        )
    }

    tagToRename?.let { oldName ->
        RenameTagDialog(
            currentName = oldName,
            onDismiss = { tagToRename = null },
            onConfirm = { newName ->
                viewModel.renameTag(oldName, newName)
                tagToRename = null
            }
        )
    }

    // Editing a category that's gone (deleted elsewhere, or not loaded yet after Android closed
    // the app) shows nothing, rather than a "New Category" form that would add a copy.
    if (showDialog && (!editingExisting || editTarget != null)) {
        CategoryDialog(
            initial = editTarget,
            siblingNames = categories.filter { it.parentId == editTarget?.parentId && it.id != editTarget?.id }.map { it.name },
            onDismiss = { showDialog = false; editTarget = null },
            onConfirm = { name, emoji, colorHex, limit ->
                val existing = editTarget
                if (existing != null) {
                    viewModel.updateCategory(existing.copy(name = name, emoji = emoji, colorHex = colorHex, monthlyLimit = limit))
                } else {
                    viewModel.addCategory(name, emoji, colorHex, limit)
                }
                showDialog = false
                editTarget = null
            }
        )
    }

    addSubcategoryFor?.let { parent ->
        CategoryDialog(
            initial = null,
            parentName = parent.name,
            siblingNames = categories.filter { it.parentId == parent.id }.map { it.name },
            onDismiss = { addSubcategoryFor = null },
            onConfirm = { name, emoji, colorHex, limit ->
                viewModel.addCategory(name, emoji, colorHex, limit, parentId = parent.id)
                addSubcategoryFor = null
            }
        )
    }
}

@Composable
private fun CategoryRow(category: Category, onEdit: () -> Unit, onDelete: () -> Unit, onAddSubcategory: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(parseColor(category.colorHex)),
            contentAlignment = Alignment.Center
        ) {
            Text(category.emoji, fontSize = 16.sp)
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(category.name, fontSize = 15.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (category.monthlyLimit > 0) "limit ₹${fmtINR(category.monthlyLimit)}" else "no limit",
                fontSize = 13.sp,
                color = Muted
            )
        }
        RowAction(Icons.Default.Add, "Add subcategory", onAddSubcategory)
        RowAction(Icons.Default.Edit, "Edit", onEdit)
        RowAction(Icons.Default.Delete, "Delete", onDelete)
    }
}

@Composable
private fun SubcategoryRow(category: Category, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 28.dp).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(parseColor(category.colorHex)))
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(category.name, fontSize = 15.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (category.monthlyLimit > 0) {
                Text("limit ₹${fmtINR(category.monthlyLimit)}", fontSize = 13.sp, color = Muted)
            }
        }
        RowAction(Icons.Default.Edit, "Edit", onEdit)
        RowAction(Icons.Default.Delete, "Delete", onDelete)
    }
}

@Composable
private fun RowAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Muted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun RenameTagDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by rememberSaveable { mutableStateOf(currentName) }
    val cleanName = Tags.cleanName(newName)
    val hasComma = ',' in newName

    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        titleContentColor = Ink,
        textContentColor = Muted,
        title = { Text("Rename tag") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New name") },
                singleLine = true,
                // A comma separates tags, so "goa,food" would become two
                isError = hasComma,
                supportingText = if (hasComma) ({ Text("A tag name can't have a comma") }) else null,
                modifier = Modifier.fillMaxWidth(),
                shape = FieldShape, colors = fieldColors()
            )
        },
        confirmButton = {
            TextButton(onClick = { cleanName?.let(onConfirm) }, enabled = cleanName != null) {
                Text("Rename", color = if (cleanName != null) Gold else Muted)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
}

@Composable
internal fun CategoryDialog(
    initial: Category?,
    parentName: String? = null,
    /** The other names under the same parent; a new or renamed category can't take one of them. */
    siblingNames: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Double) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var selectedEmoji by rememberSaveable { mutableStateOf(initial?.emoji ?: PRESET_EMOJIS.first()) }
    var selectedColor by rememberSaveable { mutableStateOf(initial?.colorHex ?: PRESET_COLORS.first()) }
    var limitText by rememberSaveable {
        mutableStateOf(formatLimitInput(initial?.monthlyLimit ?: 0.0))
    }
    val limit = rememberAmountText(limitText) { parseLimitInput(it) }
    val parsedLimit = limit.value
    // Like the bill and loan dialogs: Save is enabled only when it will save, and a wrong value
    // says why. A blank name isn't flagged before anything is typed.
    val nameError = categoryNameError(name, siblingNames, current = initial?.name)
    val canSave = nameError == null && parsedLimit != null

    MSheet(onDismiss) {
        SheetTitle(when {
            initial != null -> "Edit ${if (initial.parentId != null) "Subcategory" else "Category"}"
            parentName != null -> "Add to $parentName"
            else -> "New Category"
        })
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = name,
                // A name already longer than the limit can be shortened, not lengthened.
                onValueChange = { if (it.length <= MAX_CATEGORY_NAME_LENGTH || it.length < name.length) name = it },
                label = { Text("Name") },
                singleLine = true,
                isError = name.isNotBlank() && nameError != null,
                supportingText = if (name.isNotBlank() && nameError != null) ({ Text(nameError) }) else null,
                modifier = Modifier.fillMaxWidth(),
                shape = FieldShape, colors = fieldColors()
            )
            Text("Icon", fontSize = 13.sp, color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PRESET_EMOJIS.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (emoji == selectedEmoji) Gold else Color.Transparent)
                            .clickable { selectedEmoji = emoji },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 18.sp)
                    }
                }
            }
            Text("Color", fontSize = 13.sp, color = Muted)
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
                isError = limit.invalid,
                supportingText = limit.supportingText,
                modifier = Modifier.fillMaxWidth(),
                shape = FieldShape, colors = fieldColors()
            )
        }
        ButtonPair(
            secondary = "Cancel", onSecondary = onDismiss,
            primary = "Save",
            onPrimary = { if (canSave && parsedLimit != null) onConfirm(name.trim(), selectedEmoji, selectedColor, parsedLimit) },
            primaryEnabled = canSave
        )
    }
}
