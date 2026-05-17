package com.example.expensetracker.ui.screens.categorize

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.ui.util.parseColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorizeScreen(
    expenseId: Long,
    viewModel: CategorizeViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    var expense by remember { mutableStateOf<Expense?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf(-1L) }
    var amountText by remember { mutableStateOf("") }

    LaunchedEffect(expenseId) {
        expense = viewModel.getExpense(expenseId)
        expense?.let { amountText = "%.2f".format(it.amount) }
        loaded = true
    }

    // Auto-navigate back if expense was already deleted/dismissed
    LaunchedEffect(loaded) {
        if (loaded && expense == null) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categorize Expense") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val exp = expense
        if (!loaded || exp == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Transaction Detected", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        exp.description.take(60),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (exp.rawSms != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            exp.rawSms.take(100),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Select Category", style = MaterialTheme.typography.titleSmall)

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(categories) { cat ->
                    CategoryTile(
                        category = cat,
                        selected = cat.id == selectedCategoryId,
                        onClick = { selectedCategoryId = cat.id }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.dismissExpense(exp, onDone) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Dismiss", maxLines = 1) }

                OutlinedButton(
                    onClick = { viewModel.categorizeLater(onDone) },
                    modifier = Modifier.weight(1f)
                ) { Text("Later", maxLines = 1) }

                Button(
                    onClick = {
                        if (selectedCategoryId == -1L) return@Button
                        val amount = amountText.toDoubleOrNull() ?: exp.amount
                        viewModel.assignCategory(exp, selectedCategoryId, amount, onDone)
                    },
                    enabled = selectedCategoryId != -1L,
                    modifier = Modifier.weight(1f)
                ) { Text("Save", maxLines = 1) }
            }
        }
    }
}

@Composable
private fun CategoryTile(category: Category, selected: Boolean, onClick: () -> Unit) {
    val color = parseColor(category.colorHex)
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) color else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(category.emoji, fontSize = 24.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                category.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
