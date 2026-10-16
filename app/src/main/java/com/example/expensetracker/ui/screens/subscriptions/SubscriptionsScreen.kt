package com.example.expensetracker.ui.screens.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.subscriptions.Subscription
import com.example.expensetracker.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun formatRupees(amount: Double): String =
    "₹" + NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply { maximumFractionDigits = 0 }.format(amount)

internal fun formatShortDate(millis: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))

@Composable
fun SubscriptionsScreen(viewModel: SubscriptionsViewModel, onBack: () -> Unit) {
    val subs by viewModel.subscriptions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.message) {
        val msg = viewModel.message ?: return@LaunchedEffect
        val canUndo = viewModel.lastDismissed != null
        val result = snackbar.showSnackbar(msg, actionLabel = if (canUndo) "Undo" else null,
            duration = if (canUndo) SnackbarDuration.Long else SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDismiss()
        viewModel.clearMessage()
    }

    Scaffold(containerColor = Canvas, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
                    Text("Subscriptions", fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 28.sp, color = Ink)
                }
                Spacer(Modifier.height(12.dp))
            }
            val list = subs
            when {
                list == null -> Unit
                list.isEmpty() -> item { EmptyState() }
                else -> {
                    item { Summary(list) }
                    items(list, key = { it.key }) { s ->
                        SubscriptionCard(
                            s, categories.firstOrNull { it.id == s.categoryId },
                            canTrack = viewModel.canTrackAsBill(s),
                            onTrack = { viewModel.trackAsBill(s) },
                            onDismiss = { viewModel.dismiss(s) }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    item {
                        Text(
                            "Found from your payment history: the same payee charging a similar amount at regular intervals. " +
                                "Bills you already track aren't listed.",
                            fontSize = 12.sp, color = Muted, lineHeight = 17.sp, modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(list: List<Subscription>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Deep).padding(20.dp)) {
        Text("ESTIMATED COST", fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, letterSpacing = 1.8.sp, color = Muted)
        Text("${formatRupees(list.sumOf { it.yearlyCost })} a year",
            fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 34.sp, color = Ink)
        Text("across ${list.size} subscription${if (list.size == 1) "" else "s"}", fontSize = 13.sp, color = InkSoft)
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun SubscriptionCard(s: Subscription, category: Category?, canTrack: Boolean, onTrack: () -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Paper).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Canvas), contentAlignment = Alignment.Center) {
                Text(category?.emoji ?: "🔁", fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1)
                Text("${s.cadence.label} · next around ${formatShortDate(s.nextExpected)}", fontSize = 12.sp, color = Muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatRupees(s.amount), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text("${formatRupees(s.yearlyCost)}/yr", fontSize = 11.sp, color = Muted)
            }
        }
        Text("Paid ${s.payments} times, last on ${formatShortDate(s.lastPaid)}", fontSize = 12.sp, color = Muted,
            modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canTrack) Chip("Remind me", Jade, onTrack)
            Chip("Not a subscription", Muted, onDismiss)
        }
    }
}

@Composable
private fun Chip(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Canvas).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) { Text(text, fontSize = 12.5.sp, color = color, fontWeight = FontWeight.Medium) }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Paper).padding(20.dp)) {
        Text("No subscriptions found yet", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "MyMoney looks for the same payee charging a similar amount every week, month, 3 months or year. " +
                "A monthly subscription shows up after 3 payments.",
            fontSize = 13.sp, color = Muted, lineHeight = 19.sp
        )
    }
}
