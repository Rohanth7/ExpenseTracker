package com.example.expensetracker.ui.screens.rules

import com.example.expensetracker.ui.components.NoInsets
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.sms.ParsingRule
import com.example.expensetracker.ui.components.HeaderIcon
import com.example.expensetracker.ui.components.MCard
import com.example.expensetracker.ui.components.PrimaryButton
import com.example.expensetracker.ui.components.ScreenHeader
import com.example.expensetracker.ui.components.ScreenPadding
import com.example.expensetracker.ui.theme.*

@Composable
fun MessageRulesScreen(viewModel: MessageRulesViewModel, onTeach: () -> Unit, onBack: () -> Unit) {
    val rules by viewModel.rules.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel.message) {
        val msg = viewModel.message ?: return@LaunchedEffect
        val undo = viewModel.lastDeleted != null
        val r = snackbar.showSnackbar(msg, actionLabel = if (undo) "Undo" else null,
            duration = if (undo) SnackbarDuration.Long else SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        viewModel.clearMessage()
    }

    Scaffold(containerColor = Canvas, contentWindowInsets = NoInsets, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 24.dp)
        ) {
            item {
                ScreenHeader("Message rules", onBack = onBack)
                Text("Rules you've taught MyMoney. They're checked before its own reading of bank messages.",
                    fontSize = 13.sp, color = Muted, modifier = Modifier.padding(bottom = 12.dp))
                PrimaryButton("Teach a message", onTeach)
                Spacer(Modifier.height(16.dp))
            }
            if (rules.isEmpty()) item {
                Text("No rules yet. When a bank message is read wrong, open the expense and tap \"Read wrong? Teach MyMoney\", or paste the message here.",
                    fontSize = 13.sp, color = Muted)
            }
            items(rules, key = { it.id }) { rule ->
                MCard(Modifier.padding(bottom = 10.dp), padding = PaddingValues(start = 16.dp, top = 12.dp, end = 6.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (rule.kind == ParsingRule.Kind.PAYMENT) "PAYMENT" else "IGNORED",
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                                color = if (rule.kind == ParsingRule.Kind.PAYMENT) Gold else Muted)
                            Text(rule.label, fontSize = 15.sp, color = Ink)
                        }
                        HeaderIcon(Icons.Default.Delete, "Delete rule", onClick = { viewModel.delete(rule) }, tint = Muted)
                    }
                }
            }
        }
    }
}
