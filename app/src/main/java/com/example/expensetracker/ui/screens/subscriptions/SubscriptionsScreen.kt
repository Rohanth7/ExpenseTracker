package com.example.expensetracker.ui.screens.subscriptions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.subscriptions.Subscription
import com.example.expensetracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.example.expensetracker.ui.components.*
import com.example.expensetracker.ui.util.fmtINR
import kotlin.math.abs
import kotlin.math.roundToLong
import com.example.expensetracker.ui.util.formatRupees

internal fun formatShortDate(millis: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))

/** "↑ ₹50 since 5 Oct" for a recent price change, else null; from the prices as shown, like the alert. */
internal fun priceChangeLabel(s: Subscription): String? {
    val previous = s.previousAmount ?: return null
    val since = s.priceChangedOn ?: return null
    val change = s.amount.roundToLong() - previous.roundToLong()
    return "${if (change > 0) "↑" else "↓"} ${formatRupees(abs(change).toDouble())} since ${formatShortDate(since)}"
}

@Composable
fun SubscriptionsScreen(viewModel: SubscriptionsViewModel, onBack: () -> Unit) {
    val subs by viewModel.subscriptions.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.message) {
        val msg = viewModel.message ?: return@LaunchedEffect
        val canUndo = viewModel.lastDismissed != null
        val result = snackbar.showSnackbar(msg, actionLabel = if (canUndo) "Undo" else null,
            duration = if (canUndo) SnackbarDuration.Long else SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDismiss()
        viewModel.clearMessage()
    }

    Scaffold(containerColor = Canvas, contentWindowInsets = NoInsets, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 24.dp)
        ) {
            item { ScreenHeader("Subscriptions", onBack = onBack) }
            val list = subs
            when {
                list == null -> Unit
                list.isEmpty() -> item { EmptyState() }
                else -> {
                    item { Summary(list) }
                    items(list, key = { it.key }) { s ->
                        SubscriptionCard(
                            s,
                            canTrack = viewModel.canTrackAsBill(s),
                            onTrack = { viewModel.trackAsBill(s) },
                            onDismiss = { viewModel.dismiss(s) }
                        )
                    }
                    item {
                        Text(
                            "Found from your payment history: the same payee charging a similar amount at regular intervals. " +
                                "Bills you already track aren't listed.",
                            fontSize = 13.sp, color = Muted, lineHeight = 19.sp, modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(list: List<Subscription>) {
    val yearly = list.sumOf { it.yearlyCost }
    MCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Estimated cost", fontSize = 13.sp, color = Muted)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)) { append("₹${fmtINR((yearly / 12).roundToLong().toDouble())}") }
                        withStyle(SpanStyle(fontSize = 13.sp, color = Muted)) { append(" a month") }
                    }
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("a year", fontSize = 13.sp, color = Muted)
                Text("₹${fmtINR(yearly.roundToLong().toDouble())}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            }
        }
    }
}

@Composable
private fun SubscriptionCard(s: Subscription, canTrack: Boolean, onTrack: () -> Unit, onDismiss: () -> Unit) {
    MCard(Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
            Text(formatRupees(s.amount), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        }
        Text("${s.cadence.label} · next around ${formatShortDate(s.nextExpected)}", fontSize = 13.sp, color = Muted)
        priceChangeLabel(s)?.let { label ->
            val rise = s.priceRose
            Text(
                label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (rise) Pink else Mint,
                modifier = Modifier.padding(top = 4.dp)
                    .semantics { contentDescription = "Price ${if (rise) "up" else "down"} ${label.drop(2)}" }
            )
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (canTrack) Chip("Remind me", selected = false, onClick = onTrack)
            Chip("Not a subscription", selected = false, onClick = onDismiss)
        }
    }
}

@Composable
private fun EmptyState() {
    MCard {
        Text("No subscriptions found yet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "MyMoney looks for the same payee charging a similar amount every week, month, 3 months or year. " +
                "A monthly subscription shows up after 3 payments.",
            fontSize = 13.sp, color = Muted, lineHeight = 19.sp
        )
    }
}
