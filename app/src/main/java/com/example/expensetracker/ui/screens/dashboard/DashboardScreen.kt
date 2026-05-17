package com.example.expensetracker.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.ui.components.BarChart
import com.example.expensetracker.ui.components.BarData
import com.example.expensetracker.ui.components.PieChart
import com.example.expensetracker.ui.components.PieSlice
import com.example.expensetracker.ui.util.NotificationAccessUtil
import com.example.expensetracker.ui.util.parseColor

@Composable
fun DashboardScreen(viewModel: DashboardViewModel, onViewPending: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showIncomeDialog by remember { mutableStateOf(false) }
    val notificationAccessGranted = remember { mutableStateOf(NotificationAccessUtil.isGranted(context)) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationAccessGranted.value = NotificationAccessUtil.isGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Month navigation
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.goToPreviousMonth() }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
                }
                Text(
                    text = state.monthLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = { viewModel.goToNextMonth() },
                    enabled = state.canGoForward
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Next month",
                        tint = if (state.canGoForward) LocalContentColor.current
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    )
                }
            }
        }

        // UPI notification access banner
        if (!notificationAccessGranted.value) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    onClick = { NotificationAccessUtil.openSettings(context) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("📱", style = MaterialTheme.typography.titleLarge)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Enable UPI auto-detection",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Tap to allow notification access so GPay, PhonePe & Paytm transactions are captured automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Text("→", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Income card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Monthly Income", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showIncomeDialog = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Set income", modifier = Modifier.size(16.dp))
                        }
                    }
                    if (state.monthlyIncome > 0) {
                        Text(
                            text = "₹${"%.2f".format(state.monthlyIncome)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        val spentFraction = (state.totalSpent / state.monthlyIncome).coerceIn(0.0, 1.0).toFloat()
                        LinearProgressIndicator(
                            progress = { spentFraction },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = if (state.overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "₹${"%.2f".format(state.totalSpent)} spent  •  ₹${"%.2f".format((state.monthlyIncome - state.totalSpent).coerceAtLeast(0.0))} remaining",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    } else {
                        TextButton(onClick = { showIncomeDialog = true }, contentPadding = PaddingValues(0.dp)) {
                            Text("Tap to set your monthly income")
                        }
                    }
                }
            }
        }

        // Over budget warning
        if (state.overBudget) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "🚨 Your expenses (₹${"%.0f".format(state.totalSpent)}) have exceeded your income (₹${"%.0f".format(state.monthlyIncome)}) this month!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Total spent card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Total Spent This Month", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "₹${"%.2f".format(state.totalSpent)}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (state.overBudget) MaterialTheme.colorScheme.error else Color.Unspecified
                    )
                }
            }
        }

        // Pending badge
        if (state.pendingCount > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    onClick = onViewPending
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "⚠️ ${state.pendingCount} expense(s) need categorization",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text("→", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (state.categorySummaries.isNotEmpty()) {
            // Pie chart + legend
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Spending Breakdown", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
                        Spacer(Modifier.height(16.dp))
                        PieChart(
                            slices = state.categorySummaries.map { s ->
                                PieSlice(color = parseColor(s.category.colorHex), value = s.spent.toFloat(), label = s.category.name)
                            },
                            modifier = Modifier.size(180.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        state.categorySummaries.forEach { summary -> CategoryLegendRow(summary) }
                    }
                }
            }

            // Bar chart
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Category Spending", style = MaterialTheme.typography.titleMedium)
                        if (state.categorySummaries.any { it.category.monthlyLimit > 0 }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(12.dp, 2.dp).background(Color.Red))
                                Spacer(Modifier.width(4.dp))
                                Text("— limit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        BarChart(
                            bars = state.categorySummaries.map { s ->
                                BarData(
                                    label = s.category.name,
                                    emoji = s.category.emoji,
                                    color = parseColor(s.category.colorHex),
                                    value = s.spent,
                                    limit = s.category.monthlyLimit
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // 6-month trend chart
        if (state.monthlyTrends.any { it.total > 0 }) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("6-Month Trend", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        BarChart(
                            bars = state.monthlyTrends.mapIndexed { index, trend ->
                                BarData(
                                    label = trend.label,
                                    emoji = if (index == 5) "📍" else "·",
                                    color = if (index == 5) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                    value = trend.total
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (state.categorySummaries.isEmpty() && state.pendingCount == 0) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No expenses this month.\nTransactions from SMS will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showIncomeDialog) {
        IncomeDialog(
            currentIncome = state.monthlyIncome,
            onDismiss = { showIncomeDialog = false },
            onConfirm = { income ->
                viewModel.setIncome(income)
                showIncomeDialog = false
            }
        )
    }
}

@Composable
private fun IncomeDialog(currentIncome: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf(if (currentIncome > 0) "%.2f".format(currentIncome) else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Monthly Income") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Income (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val value = text.toDoubleOrNull() ?: return@TextButton
                onConfirm(value)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CategoryLegendRow(summary: CategorySummary) {
    val color = parseColor(summary.category.colorHex)
    val limit = summary.category.monthlyLimit
    val overLimit = limit > 0 && summary.spent > limit

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${summary.category.emoji} ${summary.category.name}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "₹${"%.0f".format(summary.spent)}",
                fontWeight = FontWeight.SemiBold,
                color = if (overLimit) MaterialTheme.colorScheme.error else Color.Unspecified,
                fontSize = 14.sp
            )
            if (limit > 0) {
                Text(
                    text = "of ₹${"%.0f".format(limit)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (limit > 0) {
        LinearProgressIndicator(
            progress = { (summary.spent / limit).coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, bottom = 4.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = if (overLimit) MaterialTheme.colorScheme.error else parseColor(summary.category.colorHex)
        )
    }
}
