package com.example.expensetracker.ui.screens.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.sms.CustomRules
import com.example.expensetracker.sms.ParsingRule
import com.example.expensetracker.ui.theme.*

/**
 * Teach MyMoney one message: paste it (or start from an expense), say whether it's a payment,
 * then tap the amount and the payee's first and last word.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeachMessageScreen(viewModel: MessageRulesViewModel, expenseId: Long?, onDone: () -> Unit, onBack: () -> Unit) {
    LaunchedEffect(expenseId) { if (expenseId != null) viewModel.loadExpense(expenseId) }
    val expense = viewModel.expense

    var text by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(expenseId == null) }
    LaunchedEffect(expense) { expense?.rawSms?.let { text = it; editing = false } }

    var isPayment by rememberSaveable { mutableStateOf(true) }
    var amountStart by rememberSaveable { mutableStateOf(-1) }
    var amountEnd by rememberSaveable { mutableStateOf(-1) }
    var payeeFrom by rememberSaveable { mutableStateOf(-1) } // word indexes
    var payeeTo by rememberSaveable { mutableStateOf(-1) }
    var error by remember { mutableStateOf<String?>(null) }

    // Slashes and pipes split words too, so "UPI/DR/123/CROMA" lets you tap just "CROMA".
    val words = remember(text) { Regex("""[^\s/|]+""").findAll(text).map { it.range }.toList() }
    val amount = if (amountStart >= 0) amountStart..amountEnd else null
    val payee = if (payeeFrom >= 0 && payeeTo >= payeeFrom && payeeTo < words.size)
        CustomRules.trimPayee(text, words[payeeFrom].first..words[payeeTo].last) else null
    fun reset() { amountStart = -1; amountEnd = -1; payeeFrom = -1; payeeTo = -1; error = null }

    fun tap(index: Int) {
        error = null
        val w = words[index]
        val amountWord = amount != null && amount.first >= w.first && amount.last <= w.last
        when {
            !isPayment -> Unit
            amountWord -> { amountStart = -1; amountEnd = -1 }
            amount == null -> {
                val r = CustomRules.amountRangeIn(text, w)
                if (r == null) error = "That word has no number in it. Tap the amount."
                else { amountStart = r.first; amountEnd = r.last }
            }
            payeeFrom < 0 -> { payeeFrom = index; payeeTo = index }
            payeeFrom == payeeTo && index > payeeFrom -> payeeTo = index
            payeeFrom == index && payeeTo == index -> { payeeFrom = -1; payeeTo = -1 }
            else -> { payeeFrom = index; payeeTo = index }
        }
    }

    Column(Modifier.fillMaxSize().background(Canvas).systemBarsPadding()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
            Text("Teach a message", fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 26.sp, color = Ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            if (editing) {
                Text("Paste a bank message that MyMoney read wrong or missed.", fontSize = 14.sp, color = InkSoft, lineHeight = 20.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it; reset() },
                    label = { Text("Message") }, minLines = 5, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Ink, unfocusedTextColor = Ink, focusedBorderColor = Jade, unfocusedBorderColor = Hairline,
                        focusedLabelColor = Jade, unfocusedLabelColor = Muted, cursorColor = Jade
                    )
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Toggle("It's a payment", isPayment) { isPayment = true; reset() }
                    Toggle("Not a payment", !isPayment) { isPayment = false; reset() }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    when {
                        !isPayment -> "Messages like this one will be ignored from now on."
                        amount == null -> "Step 1: tap the amount."
                        payee == null -> "Step 2: tap the first and last word of the shop or person paid. Skip this if the message doesn't name one."
                        else -> "Tap Save, or tap words again to change your choice."
                    },
                    fontSize = 14.sp, color = InkSoft, lineHeight = 20.sp
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Paper).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    words.forEachIndexed { i, w ->
                        val inAmount = amount != null && amount.first >= w.first && amount.last <= w.last
                        val inPayee = payee != null && i in payeeFrom..payeeTo
                        val bg = when { inAmount -> JadeDeep; inPayee -> AmberSoft; else -> Color.Transparent }
                        val fg = when { inAmount -> JadeInk; inPayee -> Amber; else -> Ink }
                        Text(
                            text.substring(w), fontSize = 14.sp, color = fg,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg)
                                .clickable(enabled = isPayment, role = Role.Button) { tap(i) }
                                .semantics { if (inAmount) contentDescription = "Amount: ${text.substring(w)}" else if (inPayee) contentDescription = "Payee: ${text.substring(w)}" }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                if (isPayment && amount != null) {
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Deep).padding(16.dp)) {
                        Text("WILL BE LOGGED AS", fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, letterSpacing = 1.8.sp, color = Muted)
                        Text("₹" + text.substring(amount), fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 28.sp, color = Ink)
                        Text(payee?.let { "to " + text.substring(it) } ?: "with the bank's name as the payee", fontSize = 14.sp, color = InkSoft)
                    }
                    if (payee == null) {
                        Spacer(Modifier.height(8.dp))
                        Text("If this message names a shop or person, mark it too. Otherwise the rule only works for this exact one.",
                            fontSize = 12.5.sp, color = Muted, lineHeight = 18.sp)
                    }
                }
                if (expense != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(if (isPayment) "Saving also corrects this expense." else "Saving also removes this expense, since it wasn't a payment.",
                        fontSize = 12.5.sp, color = Muted)
                }
            }
            error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = Coral, fontSize = 13.sp) }
            Spacer(Modifier.height(24.dp))
        }
        Box(Modifier.padding(20.dp)) {
            val ready = if (editing) text.isNotBlank() else (!isPayment || amount != null)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (ready) Jade else Jade.copy(alpha = 0.35f))
                    .clickable(enabled = ready, role = Role.Button) {
                        if (editing) { editing = false; reset() }
                        else error = viewModel.save(text, if (isPayment) ParsingRule.Kind.PAYMENT else ParsingRule.Kind.IGNORE,
                            if (isPayment) amount else null, if (isPayment) payee else null, onDone)
                    }.padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) { Text(if (editing) "Next" else "Save rule", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Deep) }
        }
    }
}

@Composable
private fun Toggle(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) Ink else Paper)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp)
    ) { Text(text, fontSize = 13.sp, color = if (selected) Canvas else Ink, fontWeight = FontWeight.Medium) }
}
