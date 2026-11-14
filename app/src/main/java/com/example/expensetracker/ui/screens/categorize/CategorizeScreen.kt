package com.example.expensetracker.ui.screens.categorize

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.db.entity.SavingsGoal
import com.example.expensetracker.ui.components.*
import com.example.expensetracker.ui.screens.dashboard.LoanProgress
import com.example.expensetracker.ui.theme.*
import com.example.expensetracker.ui.util.AmountCalculator
import com.example.expensetracker.ui.util.AmountField
import com.example.expensetracker.ui.util.AmountInputState
import com.example.expensetracker.ui.util.fmtINR
import com.example.expensetracker.ui.util.parseColor
import com.example.expensetracker.ui.util.rememberAmountInputState

private enum class CategorizeMode { Category, LoanEmi, Savings }

private val TileShape = RoundedCornerShape(14.dp)

@Composable
fun CategorizeScreen(
    expenseId: Long,
    viewModel: CategorizeViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onTeach: (Long) -> Unit = {}
) {
    val categories by viewModel.categories.collectAsState()
    val savingsGoals by viewModel.savingsGoals.collectAsState()
    val loans by viewModel.loans.collectAsState()

    var expense by remember { mutableStateOf<Expense?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(CategorizeMode.Category) }

    // Category mode state
    var selectedCategoryId by rememberSaveable { mutableStateOf(-1L) }
    val amount = rememberAmountInputState()
    var tags by rememberSaveable { mutableStateOf("") }

    // EMI mode state
    var selectedLoanId by rememberSaveable { mutableStateOf(-1L) }

    // Savings mode state
    var selectedGoalId by rememberSaveable { mutableStateOf(-1L) }
    val savingsAmount = rememberAmountInputState()

    // "Dismiss" deletes the expense, so it asks first.
    var confirmDismiss by rememberSaveable { mutableStateOf(false) }

    // Filled from the expense once: after a rotation (or Android closing the app) the fields
    // come back as the user left them, and refilling them would undo their edits.
    var filled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(expenseId) {
        expense = viewModel.getExpense(expenseId)
        expense?.takeIf { !filled }?.let {
            // Locale-free, so a comma-decimal phone shows 180.00 rather than 180,00.
            amount.setText(AmountCalculator.forEditing(it.amount))
            savingsAmount.setText(AmountCalculator.forEditing(it.amount))
            tags = it.tags
            filled = true
        }
        loaded = true
    }

    LaunchedEffect(loaded) {
        if (loaded && expense == null) onBack()
    }


    Column(Modifier.fillMaxSize().background(Canvas).padding(horizontal = ScreenPadding)) {
        ScreenHeader("What was this for?", onBack = onBack)
        val exp = expense
        if (!loaded || exp == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Gold)
            }
            return@Column
        }

        // Everything above the buttons scrolls, so they stay in reach on a short screen (landscape, large text).
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        MCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Paid to ${exp.description.take(60)}", fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center)
                // The amount can be corrected where it is filed (a category or a goal); an EMI is linked as it was read.
                when {
                    mode == CategorizeMode.Category -> AmountField(amount, large = true)
                    mode == CategorizeMode.Savings && savingsGoals.isNotEmpty() -> AmountField(savingsAmount, label = "Amount to add (₹)", large = true)
                    else -> BigNumber("₹${fmtINR(exp.amount)}", size = 32)
                }
                if (exp.rawSms != null) {
                    Text(exp.rawSms.take(100), fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center)
                }
            }
        }

        SegTabs(
            listOf("Category", "Loan EMI", "Savings goal"), mode.ordinal,
            onSelect = { mode = CategorizeMode.entries[it] }, modifier = Modifier.padding(top = 8.dp)
        )

        when (mode) {
            CategorizeMode.Category -> {
                CategoryGrid(categories, selectedCategoryId, onSelect = { selectedCategoryId = it })
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (e.g. #vacation)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = FieldShape, colors = fieldColors()
                )
            }
            CategorizeMode.LoanEmi ->
                if (loans.isEmpty()) NothingToLink("No loans added yet", "Add a loan in Plan → Loans & EMIs")
                else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    loans.forEach { loan ->
                        LoanTile(loan = loan, selected = loan.id == selectedLoanId, onClick = { selectedLoanId = loan.id })
                    }
                }
            CategorizeMode.Savings ->
                if (savingsGoals.isEmpty()) NothingToLink("No savings goals added yet", "Add a goal on the Home screen")
                else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    savingsGoals.forEach { goal ->
                        SavingsGoalTile(goal = goal, selected = goal.id == selectedGoalId, onClick = { selectedGoalId = goal.id })
                    }
                }
        }
        }

        ButtonPair(
            "Later", { viewModel.categorizeLater(onDone) }, "Save",
            onPrimary = {
                when (mode) {
                    CategorizeMode.Category -> if (selectedCategoryId != -1L) {
                        // Unreadable input shows an error; it used to fall back silently to the old amount.
                        amount.validated()?.let { viewModel.assignCategory(exp, selectedCategoryId, it, tags.trim(), onDone) }
                    }
                    CategorizeMode.LoanEmi -> loans.find { it.id == selectedLoanId }?.let { viewModel.linkToLoanEmi(exp, it, onDone) }
                    CategorizeMode.Savings -> savingsGoals.find { it.id == selectedGoalId }?.let { goal ->
                        savingsAmount.validated()?.let { viewModel.linkToSavingsGoal(exp, goal, it, onDone) }
                    }
                }
            },
            primaryEnabled = when (mode) {
                CategorizeMode.Category -> selectedCategoryId != -1L
                CategorizeMode.LoanEmi -> selectedLoanId != -1L && loans.isNotEmpty()
                CategorizeMode.Savings -> selectedGoalId != -1L && savingsGoals.isNotEmpty()
            }
        )
        Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TextAction("Dismiss", { confirmDismiss = true }, color = Pink)
            // Rules apply to bank SMS; the message text is kept for 7 days.
            if (exp.rawSms != null && (exp.source == "SMS" || exp.source == "CC-SMS")) {
                Text(
                    buildAnnotatedString {
                        append("Read wrong? ")
                        withStyle(SpanStyle(color = Gold)) { append("Teach MyMoney") }
                    },
                    fontSize = 13.sp, color = Muted,
                    modifier = Modifier.clickable { onTeach(exp.id) }.padding(vertical = 4.dp)
                )
            }
        }

        if (confirmDismiss) {
            AppAlertDialog(
                onDismissRequest = { confirmDismiss = false },
                containerColor = Paper, titleContentColor = Ink, textContentColor = Muted,
                title = { Text("Delete this expense?") },
                text = { Text("\"${exp.description.take(60)}\" will be removed from your expenses. This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = { confirmDismiss = false; viewModel.dismissExpense(exp, onDone) }) {
                        Text("Delete", color = Pink)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmDismiss = false }) { Text("Cancel", color = Muted) } }
            )
        }
    }
}

/** Three tiles to a row. A category with subcategories is a heading over its subcategories' tiles. */
@Composable
private fun CategoryGrid(categories: List<Category>, selectedCategoryId: Long, onSelect: (Long) -> Unit) {
    val parents = remember(categories) { categories.filter { it.parentId == null } }
    val childrenMap = remember(categories) { categories.filter { it.parentId != null }.groupBy { it.parentId } }

    @Composable
    fun TileRows(tiles: List<Category>) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { CategoryTile(it, selected = it.id == selectedCategoryId, onClick = { onSelect(it.id) }, modifier = Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // In the order the categories come: runs of plain categories share rows, a heading starts a new one
        var run = mutableListOf<Category>()
        val blocks = mutableListOf<Pair<Category?, List<Category>>>()
        parents.forEach { parent ->
            val children = childrenMap[parent.id]
            if (children.isNullOrEmpty()) run += parent
            else {
                if (run.isNotEmpty()) { blocks += null to run; run = mutableListOf() }
                blocks += parent to children
            }
        }
        if (run.isNotEmpty()) blocks += null to run
        blocks.forEach { (heading, tiles) ->
            if (heading != null) {
                Text(
                    "${heading.emoji} ${heading.name}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Muted,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            TileRows(tiles)
        }
    }
}

@Composable
private fun NothingToLink(title: String, hint: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(hint, fontSize = 13.sp, color = Muted)
    }
}

private fun Modifier.tile(selected: Boolean, onClick: () -> Unit) = this
    .fillMaxWidth()
    .clip(TileShape)
    .background(Paper)
    .border(2.dp, if (selected) Gold else Color.Transparent, TileShape)
    .clickable(onClick = onClick)

@Composable
private fun CategoryTile(category: Category, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.tile(selected, onClick).padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(8.dp)).background(parseColor(category.colorHex)))
        Spacer(Modifier.height(6.dp))
        Text(category.name, fontSize = 13.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LoanTile(loan: Loan, selected: Boolean, onClick: () -> Unit) {
    val remainingMonths = LoanProgress.remaining(loan, System.currentTimeMillis())
    Row(
        modifier = Modifier.tile(selected, onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(loan.emoji, fontSize = 22.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(loan.name, fontSize = 15.sp, color = Ink)
            Text("EMI ₹${fmtINR(loan.monthlyEmi)} · $remainingMonths months left", fontSize = 13.sp, color = Muted)
        }
    }
}

@Composable
private fun SavingsGoalTile(goal: SavingsGoal, selected: Boolean, onClick: () -> Unit) {
    val progress = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).toFloat() else 0f
    Column(modifier = Modifier.tile(selected, onClick).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(goal.name, fontSize = 15.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("₹${fmtINR(goal.currentAmount)} of ₹${fmtINR(goal.targetAmount)}", fontSize = 13.sp, color = Muted)
        }
        ProgressBar(progress, Mint)
    }
}
