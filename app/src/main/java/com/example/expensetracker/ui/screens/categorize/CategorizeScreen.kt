package com.example.expensetracker.ui.screens.categorize

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.db.entity.SavingsGoal
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.ui.util.categoryIcon
import com.example.expensetracker.ui.util.parseColor
import java.util.Calendar

private enum class CategorizeMode { Category, LoanEmi, Savings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorizeScreen(
    expenseId: Long,
    viewModel: CategorizeViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val savingsGoals by viewModel.savingsGoals.collectAsState()
    val loans by viewModel.loans.collectAsState()

    var expense by remember { mutableStateOf<Expense?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(CategorizeMode.Category) }

    // Category mode state
    var selectedCategoryId by remember { mutableStateOf(-1L) }
    var amountText by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }

    // EMI mode state
    var selectedLoanId by remember { mutableStateOf(-1L) }

    // Savings mode state
    var selectedGoalId by remember { mutableStateOf(-1L) }
    var savingsAmountText by remember { mutableStateOf("") }

    LaunchedEffect(expenseId) {
        expense = viewModel.getExpense(expenseId)
        expense?.let {
            amountText = "%.2f".format(it.amount)
            savingsAmountText = "%.2f".format(it.amount)
            tags = it.tags
        }
        loaded = true
    }

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
            // Transaction card
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

            // Mode tabs
            val modeLabels = listOf("Category", "Loan EMI", "Savings")
            TabRow(selectedTabIndex = mode.ordinal) {
                modeLabels.forEachIndexed { idx, label ->
                    Tab(
                        selected = mode.ordinal == idx,
                        onClick = { mode = CategorizeMode.entries[idx] },
                        text = { Text(label) }
                    )
                }
            }

            when (mode) {
                CategorizeMode.Category -> CategoryModeContent(
                    exp = exp,
                    categories = categories,
                    amountText = amountText,
                    onAmountChange = { amountText = it },
                    tags = tags,
                    onTagsChange = { tags = it },
                    selectedCategoryId = selectedCategoryId,
                    onSelectCategory = { selectedCategoryId = it },
                    onDismiss = { viewModel.dismissExpense(exp, onDone) },
                    onLater = { viewModel.categorizeLater(onDone) },
                    onSave = {
                        if (selectedCategoryId != -1L) {
                            val amount = amountText.toDoubleOrNull() ?: exp.amount
                            viewModel.assignCategory(exp, selectedCategoryId, amount, tags.trim(), onDone)
                        }
                    }
                )

                CategorizeMode.LoanEmi -> LoanEmiModeContent(
                    loans = loans,
                    selectedLoanId = selectedLoanId,
                    onSelectLoan = { selectedLoanId = it },
                    onDismiss = { viewModel.dismissExpense(exp, onDone) },
                    onLater = { viewModel.categorizeLater(onDone) },
                    onSave = {
                        if (selectedLoanId != -1L) {
                            viewModel.linkToLoanEmi(exp, onDone)
                        }
                    }
                )

                CategorizeMode.Savings -> SavingsModeContent(
                    goals = savingsGoals,
                    amountText = savingsAmountText,
                    onAmountChange = { savingsAmountText = it },
                    selectedGoalId = selectedGoalId,
                    onSelectGoal = { selectedGoalId = it },
                    onDismiss = { viewModel.dismissExpense(exp, onDone) },
                    onLater = { viewModel.categorizeLater(onDone) },
                    onSave = {
                        val goal = savingsGoals.find { it.id == selectedGoalId }
                        if (goal != null) {
                            val amount = savingsAmountText.toDoubleOrNull() ?: exp.amount
                            viewModel.linkToSavingsGoal(exp, goal, amount, onDone)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.CategoryModeContent(
    exp: Expense,
    categories: List<Category>,
    amountText: String,
    onAmountChange: (String) -> Unit,
    tags: String,
    onTagsChange: (String) -> Unit,
    selectedCategoryId: Long,
    onSelectCategory: (Long) -> Unit,
    onDismiss: () -> Unit,
    onLater: () -> Unit,
    onSave: () -> Unit
) {
    OutlinedTextField(
        value = amountText,
        onValueChange = onAmountChange,
        label = { Text("Amount (₹)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = tags,
        onValueChange = onTagsChange,
        label = { Text("Tags (e.g. #vacation)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Text("Select Category", style = MaterialTheme.typography.titleSmall)

    val parents = remember(categories) { categories.filter { it.parentId == null } }
    val childrenMap = remember(categories) { categories.filter { it.parentId != null }.groupBy { it.parentId } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.weight(1f)
    ) {
        parents.forEach { parent ->
            val children = childrenMap[parent.id]
            if (children.isNullOrEmpty()) {
                item {
                    CategoryTile(
                        category = parent,
                        selected = parent.id == selectedCategoryId,
                        onClick = { onSelectCategory(parent.id) }
                    )
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "${parent.emoji} ${parent.name}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = parseColor(parent.colorHex),
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                items(children) { child ->
                    CategoryTile(
                        category = child,
                        selected = child.id == selectedCategoryId,
                        onClick = { onSelectCategory(child.id) }
                    )
                }
            }
        }
    }

    ActionButtons(
        onDismiss = onDismiss,
        onLater = onLater,
        onSave = onSave,
        saveEnabled = selectedCategoryId != -1L
    )
}

@Composable
private fun ColumnScope.LoanEmiModeContent(
    loans: List<Loan>,
    selectedLoanId: Long,
    onSelectLoan: (Long) -> Unit,
    onDismiss: () -> Unit,
    onLater: () -> Unit,
    onSave: () -> Unit
) {
    if (loans.isEmpty()) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No loans added yet", style = MaterialTheme.typography.bodyMedium)
                Text("Add a loan in Settings → Loans & EMIs", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    } else {
        Text("Select Loan", style = MaterialTheme.typography.titleSmall)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(loans) { loan ->
                LoanTile(loan = loan, selected = loan.id == selectedLoanId, onClick = { onSelectLoan(loan.id) })
            }
        }
    }

    ActionButtons(
        onDismiss = onDismiss,
        onLater = onLater,
        onSave = onSave,
        saveEnabled = selectedLoanId != -1L && loans.isNotEmpty()
    )
}

@Composable
private fun ColumnScope.SavingsModeContent(
    goals: List<SavingsGoal>,
    amountText: String,
    onAmountChange: (String) -> Unit,
    selectedGoalId: Long,
    onSelectGoal: (Long) -> Unit,
    onDismiss: () -> Unit,
    onLater: () -> Unit,
    onSave: () -> Unit
) {
    if (goals.isEmpty()) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No savings goals added yet", style = MaterialTheme.typography.bodyMedium)
                Text("Add a goal in the Overview screen", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    } else {
        OutlinedTextField(
            value = amountText,
            onValueChange = onAmountChange,
            label = { Text("Amount to add (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Select Goal", style = MaterialTheme.typography.titleSmall)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(goals) { goal ->
                SavingsGoalTile(goal = goal, selected = goal.id == selectedGoalId, onClick = { onSelectGoal(goal.id) })
            }
        }
    }

    ActionButtons(
        onDismiss = onDismiss,
        onLater = onLater,
        onSave = onSave,
        saveEnabled = selectedGoalId != -1L && goals.isNotEmpty()
    )
}

@Composable
private fun ActionButtons(
    onDismiss: () -> Unit,
    onLater: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) { Text("Dismiss", maxLines = 1) }

        OutlinedButton(
            onClick = onLater,
            modifier = Modifier.weight(1f)
        ) { Text("Later", maxLines = 1) }

        Button(
            onClick = onSave,
            enabled = saveEnabled,
            modifier = Modifier.weight(1f)
        ) { Text("Save", maxLines = 1) }
    }
}

@Composable
private fun CategoryTile(category: Category, selected: Boolean, onClick: () -> Unit) {
    val color = parseColor(category.colorHex)
    val isParent = category.parentId == null
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
            if (isParent) {
                Box(
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = if (selected) 0.35f else 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(categoryIcon(category.name), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                }
            } else {
                Text(category.emoji, fontSize = 22.sp)
            }
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

@Composable
private fun LoanTile(loan: Loan, selected: Boolean, onClick: () -> Unit) {
    val remainingMonths = run {
        val startCal = Calendar.getInstance().apply { timeInMillis = loan.startDate }
        val nowCal = Calendar.getInstance()
        val elapsed = (nowCal.get(Calendar.YEAR) - startCal.get(Calendar.YEAR)) * 12 +
            (nowCal.get(Calendar.MONTH) - startCal.get(Calendar.MONTH))
        (loan.tenureMonths - elapsed).coerceAtLeast(0)
    }
    Card(
        modifier = Modifier.fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(loan.emoji, fontSize = 28.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(loan.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "EMI ₹${loan.monthlyEmi.toLong()} · $remainingMonths months left",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun SavingsGoalTile(goal: SavingsGoal, selected: Boolean, onClick: () -> Unit) {
    val color = parseColor(goal.colorHex)
    val progress = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
    Card(
        modifier = Modifier.fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) color else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(goal.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = color
            )
            Text(
                "₹${goal.currentAmount.toLong()} of ₹${goal.targetAmount.toLong()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
