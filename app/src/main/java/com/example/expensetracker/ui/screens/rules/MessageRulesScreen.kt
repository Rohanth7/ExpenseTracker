package com.example.expensetracker.ui.screens.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.sms.ParsingRule
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

    Scaffold(containerColor = Canvas, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
                    Text("Message rules", fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 28.sp, color = Ink)
                }
                Text("Rules you've taught MyMoney. They're checked before its own reading of bank messages.",
                    fontSize = 13.sp, color = Muted, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Jade)
                        .clickable(role = Role.Button, onClick = onTeach).padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Teach a message", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Deep) }
                Spacer(Modifier.height(16.dp))
            }
            if (rules.isEmpty()) item {
                Text("No rules yet. When a bank message is read wrong, open the expense and tap \"Read wrong? Teach MyMoney\", or paste the message here.",
                    fontSize = 13.sp, color = Muted, lineHeight = 19.sp, modifier = Modifier.padding(4.dp))
            }
            items(rules, key = { it.id }) { rule ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(Paper).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (rule.kind == ParsingRule.Kind.PAYMENT) "PAYMENT" else "IGNORED",
                            fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.5.sp,
                            color = if (rule.kind == ParsingRule.Kind.PAYMENT) Jade else Muted)
                        Text(rule.label, fontSize = 13.sp, color = Ink, lineHeight = 18.sp)
                    }
                    IconButton(onClick = { viewModel.delete(rule) }) { Icon(Icons.Default.Delete, "Delete rule", tint = Muted) }
                }
            }
        }
    }
}
