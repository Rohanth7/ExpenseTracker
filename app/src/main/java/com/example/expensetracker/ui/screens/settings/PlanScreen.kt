package com.example.expensetracker.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.budget.Budget
import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.db.entity.RecurringIncome
import com.example.expensetracker.data.period.BudgetPeriod
import com.example.expensetracker.ui.components.*
import com.example.expensetracker.ui.screens.dashboard.LoanProgress
import com.example.expensetracker.ui.theme.*
import com.example.expensetracker.ui.util.AmountCalculator
import com.example.expensetracker.ui.util.AmountField
import com.example.expensetracker.ui.util.fmtINR
import com.example.expensetracker.ui.util.formatLimitInput
import com.example.expensetracker.ui.util.oneCharacter
import com.example.expensetracker.ui.util.parseAmountOrZero
import com.example.expensetracker.ui.util.parseColor
import com.example.expensetracker.ui.util.parseLimitInput
import com.example.expensetracker.ui.util.rememberAmountInputState
import com.example.expensetracker.ui.util.rememberAmountText
import com.example.expensetracker.ui.util.rememberSaveableItem
import com.example.expensetracker.ui.util.wholeNumberIn
import java.util.Calendar

/* The Plan tab: limits, bills and loans at a glance, and the pages that manage each. */

/** "15th · remind 2 days before", or "15th · logged automatically" for an auto-log bill. */
internal fun billSchedule(bill: Bill): String =
    if (bill.autoLog) "${BudgetPeriod.ordinal(bill.dueDayOfMonth)} · logged automatically"
    else "${BudgetPeriod.ordinal(bill.dueDayOfMonth)} · remind ${bill.reminderDays} ${if (bill.reminderDays == 1) "day" else "days"} before"

@Composable
internal fun PlanFrontPage(
    budgets: List<Budget>,
    alertPercent: Int,
    bills: List<Bill>,
    loans: List<Loan>,
    categories: List<Category>,
    recurringIncomeCount: Int,
    onOpenSubscriptions: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigate: (SettingsPage) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Canvas),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 24.dp)
    ) {
        item { ScreenHeader("Plan") }

        item {
            SectionLabel("Monthly limits", action = "Manage", onAction = { onNavigate(SettingsPage.CategoryBudgets) })
            if (budgets.isEmpty()) {
                MCard { Text("No limits yet. Set one to get a warning before you overspend.", fontSize = 13.sp, color = Muted) }
            } else {
                MCard(padding = RowCardPadding) {
                    budgets.forEachIndexed { i, budget ->
                        if (i > 0) RowDivider()
                        BudgetRow(budget.name, budget.spent, budget.limit, alertPercent)
                    }
                }
            }
        }

        item {
            SectionLabel("Bills and reminders", action = "All bills", onAction = { onNavigate(SettingsPage.Bills) })
            val shown = bills.filter { it.isEnabled }.take(3)
            if (shown.isEmpty()) {
                MCard { Text("No bills set up", fontSize = 13.sp, color = Muted) }
            } else {
                MCard(padding = RowCardPadding) {
                    shown.forEachIndexed { i, bill ->
                        if (i > 0) RowDivider()
                        AccentRow(
                            accent = categories.find { it.id == bill.categoryId }?.let { parseColor(it.colorHex) } ?: Gold,
                            title = bill.name,
                            subtitle = billSchedule(bill),
                            trailing = "₹${fmtINR(bill.amount)}"
                        )
                    }
                }
            }
        }

        item { SectionLabel("Loans and EMIs", action = "All loans", onAction = { onNavigate(SettingsPage.Loans) }) }
        if (loans.isEmpty()) {
            item { MCard { Text("No loans tracked", fontSize = 13.sp, color = Muted) } }
        } else {
            items(loans.size) { i ->
                val loan = loans[i]
                // The Overview counts the same way (LoanProgress)
                val paid = LoanProgress.paid(loan, System.currentTimeMillis())
                MCard(Modifier.padding(top = if (i == 0) 0.dp else 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(loan.name, fontSize = 15.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("$paid of ${loan.tenureMonths} paid", fontSize = 13.sp, color = Muted)
                    }
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(paid.toFloat() / loan.tenureMonths, Pink)
                }
            }
        }

        item {
            MCard(Modifier.padding(top = 10.dp), padding = RowCardPadding) {
                MenuRow("Subscriptions", "Repeating payments found in your spending", onOpenSubscriptions)
                RowDivider()
                MenuRow(
                    "Recurring income",
                    when (recurringIncomeCount) { 0 -> "None yet"; 1 -> "1 income"; else -> "$recurringIncomeCount incomes" },
                    onClick = { onNavigate(SettingsPage.Bills) }
                )
            }
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    MCard {
        Text(text, fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center, lineHeight = 19.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
    }
}

@Composable
internal fun CategoryBudgetsSubPage(
    categories: List<Category>,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    var categoryToEdit by rememberSaveableItem(categories) { it.id }
    val parentCategories = categories.filter { it.parentId == null }
    // Each category, then its subcategories: a subcategory can have a limit of its own.
    val rows = parentCategories.flatMap { parent -> listOf(parent) + categories.filter { it.parentId == parent.id } }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(Canvas),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 40.dp)
    ) {
        item { ScreenHeader("Category Budgets", onBack = onBack) }
        item {
            SectionLabel("Monthly limits")
            Text(
                "Set a monthly cap per category. You'll be alerted when spending approaches or exceeds the limit. A subcategory's spending counts toward its own limit and its category's.",
                fontSize = 13.sp, color = Muted, lineHeight = 19.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        if (parentCategories.isEmpty()) {
            item { EmptyCard("No categories yet.\nAdd expenses to create categories first.") }
        } else {
            item {
                MCard(padding = RowCardPadding) {
                    rows.forEachIndexed { i, cat ->
                        if (i > 0) RowDivider()
                        CategoryLimitRow(category = cat, onClick = { categoryToEdit = cat })
                    }
                }
            }
        }
    }

    categoryToEdit?.let { cat ->
        CategoryLimitSheet(
            category = cat,
            onDismiss = { categoryToEdit = null },
            onConfirm = { limit ->
                viewModel.updateCategoryLimit(cat, limit)
                categoryToEdit = null
            }
        )
    }
}

@Composable
private fun CategoryLimitRow(category: Category, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(parseColor(category.colorHex)),
            contentAlignment = Alignment.Center
        ) {
            Text(category.emoji, fontSize = 16.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(category.name, fontSize = 15.sp, color = Ink)
            Text(
                if (category.monthlyLimit > 0) "₹${fmtINR(category.monthlyLimit)} / month" else "No limit",
                fontSize = 13.sp,
                color = if (category.monthlyLimit > 0) Gold else Muted
            )
        }
        Icon(Icons.Default.ChevronRight, null, tint = Ink, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CategoryLimitSheet(
    category: Category,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var limitText by rememberSaveable {
        mutableStateOf(formatLimitInput(category.monthlyLimit))
    }
    val limit = rememberAmountText(limitText) { parseLimitInput(it) }
    val parsedLimit = limit.value

    MSheet(onDismiss) {
        SheetTitle("${category.emoji} ${category.name}")
        OutlinedTextField(
            value = limitText,
            onValueChange = { limitText = it },
            label = { Text("Monthly limit (₹)") },
            placeholder = { Text("e.g. 5000") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = limit.invalid,
            supportingText = limit.supportingText,
            modifier = Modifier.fillMaxWidth(),
            shape = FieldShape, colors = fieldColors()
        )
        if (category.monthlyLimit > 0) {
            Text("Set to 0 or leave blank to remove the limit.", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
        }
        ButtonPair("Cancel", onDismiss, "Save", { parsedLimit?.let(onConfirm) }, primaryEnabled = parsedLimit != null)
    }
}

@Composable
internal fun BillsSubPage(bills: List<Bill>, categories: List<Category>, viewModel: SettingsViewModel, modifier: Modifier = Modifier, onBack: () -> Unit) {
    var showAddBill by rememberSaveable { mutableStateOf(false) }
    var billToDelete by rememberSaveableItem(bills) { it.id }
    var billToEdit by rememberSaveableItem(bills) { it.id }
    val recurringIncomes by viewModel.recurringIncomes.collectAsState()
    var showAddIncome by rememberSaveable { mutableStateOf(false) }
    var incomeToEdit by rememberSaveableItem(recurringIncomes) { it.id }
    var incomeToDelete by rememberSaveableItem(recurringIncomes) { it.id }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(Canvas),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 40.dp)
    ) {
        item { ScreenHeader("Bills & Reminders", onBack = onBack) }
        item { SectionLabel("Bills & recurring", action = "Add +", onAction = { showAddBill = true }) }
        if (bills.isEmpty()) {
            item { EmptyCard("No bills set up yet.\nTap Add + to track subscriptions, rent, or auto-log recurring expenses.") }
        } else {
            items(bills) { bill ->
                ScheduleRow(
                    accent = categories.find { it.id == bill.categoryId }?.let { parseColor(it.colorHex) } ?: Gold,
                    name = bill.name,
                    detail = if (bill.autoLog) "₹${fmtINR(bill.amount)} · logs on ${BudgetPeriod.ordinal(bill.dueDayOfMonth)} of month"
                        else "₹${fmtINR(bill.amount)} · due ${BudgetPeriod.ordinal(bill.dueDayOfMonth)} · remind ${bill.reminderDays}d before",
                    enabled = bill.isEnabled,
                    onToggle = { viewModel.toggleBill(bill) }, onDelete = { billToDelete = bill }, onClick = { billToEdit = bill }
                )
                Spacer(Modifier.height(10.dp))
            }
        }
        item { SectionLabel("Recurring income", action = "Add +", onAction = { showAddIncome = true }) }
        if (recurringIncomes.isEmpty()) {
            item { EmptyCard("No recurring income yet.\nAdd your salary and it's logged as income on its day each month.") }
        } else {
            items(recurringIncomes, key = { "income_${it.id}" }) { r ->
                ScheduleRow(
                    accent = Mint,
                    name = r.name,
                    detail = "+₹${fmtINR(r.amount)} · logged on the ${BudgetPeriod.ordinal(r.dayOfMonth)}${if (r.dayOfMonth > 28) " (or the month's last day)" else ""}",
                    enabled = r.isEnabled,
                    onToggle = { viewModel.toggleRecurringIncome(r) }, onDelete = { incomeToDelete = r }, onClick = { incomeToEdit = r }
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    if (showAddIncome) {
        RecurringIncomeSheet(existing = null, onDismiss = { showAddIncome = false },
            onConfirm = { viewModel.addRecurringIncome(it); showAddIncome = false })
    }
    incomeToEdit?.let { r ->
        RecurringIncomeSheet(existing = r, onDismiss = { incomeToEdit = null },
            onConfirm = { viewModel.updateRecurringIncome(it); incomeToEdit = null })
    }
    incomeToDelete?.let { r ->
        AppAlertDialog(onDismissRequest = { incomeToDelete = null }, containerColor = Paper, titleContentColor = Ink, textContentColor = Muted,
            title = { Text("Delete recurring income?") },
            text = { Text("\"${r.name}\" won't be logged any more. Income it already logged stays.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteRecurringIncome(r); incomeToDelete = null }) { Text("Delete", color = Pink) } },
            dismissButton = { TextButton(onClick = { incomeToDelete = null }) { Text("Cancel", color = Muted) } })
    }

    if (showAddBill) {
        AddBillDialog(categories = categories, onDismiss = { showAddBill = false },
            onConfirm = { name, amount, day, reminderDays, catId, autoLog ->
                viewModel.addBill(name, amount, day, reminderDays, catId, autoLog); showAddBill = false
            })
    }
    billToDelete?.let { bill ->
        AppAlertDialog(onDismissRequest = { billToDelete = null }, containerColor = Paper, titleContentColor = Ink, textContentColor = Muted,
            title = { Text("Delete bill?") }, text = { Text("\"${bill.name}\" will be removed from your bill reminders.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteBill(bill); billToDelete = null }) { Text("Delete", color = Pink) } },
            dismissButton = { TextButton(onClick = { billToDelete = null }) { Text("Cancel", color = Muted) } })
    }
    billToEdit?.let { bill ->
        EditBillDialog(bill = bill, categories = categories, onDismiss = { billToEdit = null },
            onConfirm = { updated -> viewModel.updateBill(updated); billToEdit = null })
    }
}

/** A bill or a recurring income on its page: tap to edit, switch it off, or delete it. */
@Composable
private fun ScheduleRow(
    accent: androidx.compose.ui.graphics.Color, name: String, detail: String, enabled: Boolean,
    onToggle: () -> Unit, onDelete: () -> Unit, onClick: () -> Unit
) {
    MCard(onClick = onClick, padding = PaddingValues(start = 16.dp, end = 8.dp)) {
        // The detail can run to two lines ("… logged on the 31st (or the month's last day)")
        AccentRow(accent, name, detail, trailing = "", subtitleMaxLines = 2, actions = {
            Switch(checked = enabled, onCheckedChange = { onToggle() }, colors = switchColors())
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = Muted, modifier = Modifier.size(20.dp)) }
        })
    }
}

@Composable
internal fun LoansSubPage(loans: List<Loan>, viewModel: SettingsViewModel, modifier: Modifier = Modifier, onBack: () -> Unit) {
    var showAddLoan by rememberSaveable { mutableStateOf(false) }
    var loanToDelete by rememberSaveableItem(loans) { it.id }
    var loanToEdit by rememberSaveableItem(loans) { it.id }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(Canvas),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 40.dp)
    ) {
        item { ScreenHeader("Loans & EMIs", onBack = onBack) }
        item { SectionLabel("Loans", action = "Add +", onAction = { showAddLoan = true }) }
        if (loans.isEmpty()) {
            item { EmptyCard("No loans tracked.\nTap Add + to monitor EMI progress.") }
        } else {
            items(loans) { loan ->
                LoanRow(loan = loan, onDelete = { loanToDelete = loan }, onClick = { loanToEdit = loan })
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    if (showAddLoan) {
        AddLoanDialog(onDismiss = { showAddLoan = false },
            onConfirm = { name, emoji, total, emi, tenure, startDate, dueDay ->
                viewModel.addLoan(name, emoji, total, emi, tenure, startDate, dueDay); showAddLoan = false
            })
    }
    loanToDelete?.let { loan ->
        AppAlertDialog(onDismissRequest = { loanToDelete = null }, containerColor = Paper, titleContentColor = Ink, textContentColor = Muted,
            title = { Text("Delete loan?") }, text = { Text("\"${loan.name}\" will be removed from your EMI tracker.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteLoan(loan); loanToDelete = null }) { Text("Delete", color = Pink) } },
            dismissButton = { TextButton(onClick = { loanToDelete = null }) { Text("Cancel", color = Muted) } })
    }
    loanToEdit?.let { loan ->
        EditLoanDialog(loan = loan, onDismiss = { loanToEdit = null },
            onConfirm = { updated -> viewModel.updateLoan(updated); loanToEdit = null })
    }
}

@Composable
private fun LoanRow(loan: Loan, onDelete: () -> Unit, onClick: () -> Unit) {
    // The Overview counts the same way (LoanProgress)
    val paidMonths = LoanProgress.paid(loan, System.currentTimeMillis())
    val remaining = loan.tenureMonths - paidMonths

    MCard(onClick = onClick, padding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(loan.emoji, fontSize = 20.sp)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(loan.name, fontSize = 15.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("₹${fmtINR(loan.monthlyEmi)}/mo · ${loan.tenureMonths} months", fontSize = 13.sp, color = Muted)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = Muted, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(paidMonths.toFloat() / loan.tenureMonths, Pink, Modifier.padding(end = 8.dp))
        Text("$paidMonths paid · $remaining remaining", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
    }
}

/** A switch with its label in a Field-coloured box, for the sheets. */
@Composable
private fun SwitchField(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(FieldShape).background(Field).clickable { onChange(!checked) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 15.sp, color = Ink)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = switchColors())
    }
}

/** Add ([existing] null) or edit a recurring income: name, amount, day of the month, on/off. Mirrors the bill sheets. */
@Composable
private fun RecurringIncomeSheet(existing: RecurringIncome?, onDismiss: () -> Unit, onConfirm: (RecurringIncome) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    val amount = rememberAmountInputState(existing?.let { AmountCalculator.forEditing(it.amount) } ?: "")
    var day by rememberSaveable { mutableStateOf(existing?.dayOfMonth?.toString() ?: "") }
    var enabled by rememberSaveable { mutableStateOf(existing?.isEnabled ?: true) }
    val dayValue = day.toIntOrNull()?.takeIf { it in 1..31 }

    MSheet(onDismiss) {
        SheetTitle(if (existing == null) "Add Recurring Income" else "Edit Recurring Income")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (e.g. Salary)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = FieldShape, colors = fieldColors())
            AmountField(amount)
            OutlinedTextField(value = day, onValueChange = { if (it.length <= 2) day = it.filter { c -> c.isDigit() } },
                label = { Text("Day of the month (1–31)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = FieldShape, colors = fieldColors(),
                isError = day.isNotEmpty() && dayValue == null,
                supportingText = { Text(if (day.isNotEmpty() && dayValue == null) "Enter a day from 1 to 31" else "Logged as income on this day; 31 means the month's last day") })
            SwitchField("Log it each month", null, enabled) { enabled = it }
        }
        ButtonPair("Cancel", onDismiss, if (existing == null) "Add" else "Save", {
            val amt = amount.validated() ?: return@ButtonPair
            val d = dayValue ?: return@ButtonPair
            val base = existing ?: RecurringIncome(name = "", amount = 0.0, dayOfMonth = 1)
            onConfirm(base.copy(name = name.trim(), amount = amt, dayOfMonth = d, isEnabled = enabled))
        }, primaryEnabled = name.isNotBlank() && dayValue != null)
    }
}

/** A bill's category picker when it has none: an auto-logged expense is then pending. */
private const val NO_CATEGORY = "None"

@Composable
internal fun AddBillDialog(categories: List<Category>, onDismiss: () -> Unit, onConfirm: (String, Double, Int, Int, Long, Boolean) -> Unit) {
    BillSheet("Add Bill / Recurring", bill = null, categories, onDismiss, "Add", onConfirm)
}

@Composable
internal fun EditBillDialog(bill: Bill, categories: List<Category>, onDismiss: () -> Unit, onConfirm: (Bill) -> Unit) {
    BillSheet("Edit Bill", bill, categories, onDismiss, "Save") { name, amount, day, remind, categoryId, autoLog ->
        onConfirm(bill.copy(name = name, amount = amount, dueDayOfMonth = day, reminderDays = remind, categoryId = categoryId, autoLog = autoLog))
    }
}

/** The add ([bill] null) and edit bill sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillSheet(
    title: String, bill: Bill?, categories: List<Category>, onDismiss: () -> Unit, confirmText: String,
    onConfirm: (name: String, amount: Double, dueDay: Int, reminderDays: Int, categoryId: Long, autoLog: Boolean) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(bill?.name ?: "") }
    var amount by rememberSaveable { mutableStateOf(bill?.let { AmountCalculator.forEditing(it.amount) } ?: "") }
    var autoLog by rememberSaveable { mutableStateOf(bill?.autoLog ?: false) }
    // Above 0: an auto-log bill adds its amount as an expense every month. A reminder-only bill
    // saved with ₹0 before that rule may keep its 0, so its name, due day and reminder can still
    // be changed; turning auto-log on asks for an amount.
    val amountText = if (bill != null && bill.amount == 0.0 && !autoLog) rememberAmountText(amount) { parseAmountOrZero(it) }
        else rememberAmountText(amount) { AmountCalculator.evaluate(it) }
    var dueDay by rememberSaveable { mutableStateOf(bill?.dueDayOfMonth?.toString() ?: "") }
    var reminderDays by rememberSaveable { mutableStateOf(bill?.reminderDays?.toString() ?: "3") }
    // Optional: none unless the user picks one.
    var selectedCategoryId by rememberSaveable { mutableStateOf(bill?.categoryId ?: -1L) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedCategory = categories.find { it.id == selectedCategoryId }
    val dayValue = wholeNumberIn(dueDay, 1..31)
    val remindValue = wholeNumberIn(reminderDays, 0..14)
    val canSave = name.isNotBlank() && amountText.value != null && dayValue != null && (autoLog || remindValue != null)

    MSheet(onDismiss) {
        SheetTitle(title)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(if (bill == null) "Name (e.g. Rent, Netflix)" else "Name") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = FieldShape, colors = fieldColors())
            SwitchField(
                "Auto-log expense monthly",
                if (autoLog) "Expense added on its due day each month" else "Send a reminder notification instead",
                autoLog
            ) { autoLog = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f),
                    isError = amountText.invalid, supportingText = amountText.supportingText, shape = FieldShape, colors = fieldColors())
                // Auto-log bills have a due day too: the day they're logged (31 = the month's last day).
                OutlinedTextField(value = dueDay, onValueChange = { if (it.length <= 2) dueDay = it.filter { c -> c.isDigit() } },
                    label = { Text("Due day") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(0.7f),
                    isError = dueDay.isNotEmpty() && dayValue == null,
                    supportingText = if (dueDay.isNotEmpty() && dayValue == null) ({ Text("Enter 1 to 31") }) else null,
                    shape = FieldShape, colors = fieldColors())
            }
            if (!autoLog) {
                OutlinedTextField(value = reminderDays, onValueChange = { reminderDays = it.filter { c -> c.isDigit() } },
                    label = { Text("Remind N days before") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
                    isError = reminderDays.isNotEmpty() && remindValue == null,
                    supportingText = if (reminderDays.isNotEmpty() && remindValue == null) ({ Text("Enter 0 to 14 days") }) else null,
                    shape = FieldShape, colors = fieldColors())
            }
            if (categories.isNotEmpty()) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(value = selectedCategory?.let { "${it.emoji} ${it.name}" } ?: NO_CATEGORY,
                        onValueChange = {}, readOnly = true, label = { Text("Category (optional)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                        shape = FieldShape, colors = fieldColors())
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Field) {
                        DropdownMenuItem(text = { Text(NO_CATEGORY, color = Muted) }, onClick = { selectedCategoryId = -1L; expanded = false })
                        categories.forEach { cat ->
                            DropdownMenuItem(text = { Text("${cat.emoji} ${cat.name}", color = Ink) }, onClick = { selectedCategoryId = cat.id; expanded = false })
                        }
                    }
                }
            }
        }
        ButtonPair("Cancel", onDismiss, confirmText, {
            val amt = amountText.value ?: return@ButtonPair
            val day = dayValue ?: return@ButtonPair
            val remind = if (autoLog) 0 else remindValue ?: return@ButtonPair
            onConfirm(name.trim(), amt, day, remind, selectedCategoryId, autoLog)
        }, primaryEnabled = canSave)
    }
}

/** 100 years: enough for any loan, and keeps the month arithmetic in range. */
private const val MAX_TENURE_MONTHS = 1200

@Composable
internal fun AddLoanDialog(onDismiss: () -> Unit, onConfirm: (String, String, Double, Double, Int, Long, Int) -> Unit) {
    LoanSheet("Add Loan / EMI", loan = null, onDismiss, "Add", onConfirm)
}

@Composable
internal fun EditLoanDialog(loan: Loan, onDismiss: () -> Unit, onConfirm: (Loan) -> Unit) {
    LoanSheet("Edit Loan", loan, onDismiss, "Save") { name, emoji, total, emi, tenure, startDate, dueDay ->
        onConfirm(loan.copy(name = name, emoji = emoji, totalAmount = total, monthlyEmi = emi, tenureMonths = tenure, startDate = startDate, dueDayOfMonth = dueDay))
    }
}

/** The add ([loan] null) and edit loan sheet. Editing shows how far the loan has got. */
@Composable
private fun LoanSheet(
    title: String, loan: Loan?, onDismiss: () -> Unit, confirmText: String,
    onConfirm: (name: String, emoji: String, total: Double, emi: Double, tenure: Int, startDate: Long, dueDay: Int) -> Unit
) {
    val initialMonthsAgo = loan?.let {
        val now = Calendar.getInstance()
        val start = Calendar.getInstance().apply { timeInMillis = it.startDate }
        ((now.get(Calendar.YEAR) - start.get(Calendar.YEAR)) * 12 + (now.get(Calendar.MONTH) - start.get(Calendar.MONTH))).coerceAtLeast(0)
    } ?: 0

    var name by rememberSaveable { mutableStateOf(loan?.name ?: "") }
    var emoji by rememberSaveable { mutableStateOf(loan?.emoji ?: "🏦") }
    // To the paisa: toLong() here cut the paise off an untouched loan when it was saved.
    var totalAmount by rememberSaveable { mutableStateOf(loan?.let { AmountCalculator.forEditing(it.totalAmount) } ?: "") }
    var monthlyEmi by rememberSaveable { mutableStateOf(loan?.let { AmountCalculator.forEditing(it.monthlyEmi) } ?: "") }
    // The total may be 0 (only the EMI known), as before; the EMI may not.
    val totalText = rememberAmountText(totalAmount) { parseAmountOrZero(it) }
    val emiText = rememberAmountText(monthlyEmi) { AmountCalculator.evaluate(it) }
    val amountsValid = totalText.value != null && emiText.value != null
    var tenureMonths by rememberSaveable { mutableStateOf(loan?.tenureMonths?.toString() ?: "") }
    var monthsAgo by rememberSaveable { mutableStateOf(initialMonthsAgo.toString()) }
    var dueDay by rememberSaveable { mutableStateOf(loan?.dueDayOfMonth?.toString() ?: "5") }
    val tenureValue = wholeNumberIn(tenureMonths, 1..MAX_TENURE_MONTHS)
    val dueValue = wholeNumberIn(dueDay, 1..31)
    // Blank isn't 0: saved as 0 it moved the first EMI to this month and reset the loan's progress.
    val agoValue = wholeNumberIn(monthsAgo, 0..MAX_TENURE_MONTHS)
    val canSave = name.isNotBlank() && amountsValid && tenureValue != null && dueValue != null && agoValue != null

    MSheet(onDismiss) {
        SheetTitle(title)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (loan != null) {
                // The Overview counts the same way (LoanProgress)
                val paidMonths = LoanProgress.paid(loan, System.currentTimeMillis())
                val progress = (paidMonths.toFloat() / loan.tenureMonths).coerceIn(0f, 1f)
                MCard(padding = PaddingValues(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("$paidMonths of ${loan.tenureMonths} EMIs paid", fontSize = 13.sp, color = Muted)
                        Text("${Math.round(progress * 100)}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Pink)
                    }
                    Spacer(Modifier.height(6.dp))
                    ProgressBar(progress, Pink)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = emoji, onValueChange = { emoji = oneCharacter(emoji, it) }, label = { Text("Icon") }, singleLine = true, modifier = Modifier.width(72.dp),
                    shape = FieldShape, colors = fieldColors())
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Loan name") }, singleLine = true, modifier = Modifier.weight(1f),
                    shape = FieldShape, colors = fieldColors())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = totalAmount, onValueChange = { totalAmount = it }, label = { Text("Total (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f),
                    isError = totalText.invalid, supportingText = totalText.supportingText, shape = FieldShape, colors = fieldColors())
                OutlinedTextField(value = monthlyEmi, onValueChange = { monthlyEmi = it }, label = { Text("EMI (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f),
                    isError = emiText.invalid, supportingText = emiText.supportingText, shape = FieldShape, colors = fieldColors())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = tenureMonths, onValueChange = { tenureMonths = it.filter { c -> c.isDigit() } }, label = { Text("Tenure (mo)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f),
                    isError = tenureMonths.isNotEmpty() && tenureValue == null,
                    supportingText = if (tenureMonths.isNotEmpty() && tenureValue == null) ({ Text("1 to $MAX_TENURE_MONTHS months") }) else null,
                    shape = FieldShape, colors = fieldColors())
                OutlinedTextField(value = monthsAgo, onValueChange = { monthsAgo = it.filter { c -> c.isDigit() } }, label = { Text("1st EMI, months ago") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f),
                    isError = agoValue == null,
                    supportingText = if (agoValue == null) ({ Text("0 to $MAX_TENURE_MONTHS months") }) else null,
                    shape = FieldShape, colors = fieldColors())
            }
            OutlinedTextField(value = dueDay, onValueChange = { if (it.length <= 2) dueDay = it.filter { c -> c.isDigit() } }, label = { Text("Due day of month") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
                isError = dueDay.isNotEmpty() && dueValue == null,
                supportingText = if (dueDay.isNotEmpty() && dueValue == null) ({ Text("Enter 1 to 31") }) else null,
                shape = FieldShape, colors = fieldColors())
        }
        ButtonPair("Cancel", onDismiss, confirmText, {
            val total = totalText.value ?: return@ButtonPair
            val emi = emiText.value ?: return@ButtonPair
            val tenure = tenureValue ?: return@ButtonPair
            val due = dueValue ?: return@ButtonPair
            val ago = agoValue ?: return@ButtonPair
            val startDate = Calendar.getInstance().apply {
                add(Calendar.MONTH, -ago)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            onConfirm(name.trim(), emoji.ifBlank { "🏦" }, total, emi, tenure, startDate, due)
        }, primaryEnabled = canSave)
    }
}
