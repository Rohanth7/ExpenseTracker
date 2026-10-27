package com.example.expensetracker.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.expensetracker.ui.theme.Jade
import com.example.expensetracker.ui.theme.Muted
import com.example.expensetracker.ui.util.PastOrTodayDates
import com.example.expensetracker.ui.util.toUtcMidnight
import com.example.expensetracker.ui.util.utcMidnightToLocalDayEnd
import com.example.expensetracker.ui.util.utcMidnightToLocalDayStart

/**
 * Pick a range of past days. [initial] and the result are local (start of the first day,
 * 23:59:59.999 of the last); one day picked means a one-day range.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(title: String, initial: Pair<Long, Long>?, onDismiss: () -> Unit, onPick: (Pair<Long, Long>) -> Unit) {
    // Only past days can be picked, so a range running past today (e.g. this month) opens ending today.
    val today = toUtcMidnight(System.currentTimeMillis())
    val (initialStart, initialEnd) = initial
        ?.let { (start, end) -> toUtcMidnight(start) to toUtcMidnight(end).coerceAtMost(today) }
        ?.takeIf { (start, _) -> start <= today } ?: (null to null)
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart,
        initialSelectedEndDateMillis = initialEnd,
        selectableDates = PastOrTodayDates
    )
    AppDatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val start = state.selectedStartDateMillis
            val end = state.selectedEndDateMillis ?: start
            TextButton(enabled = start != null, onClick = {
                onPick(utcMidnightToLocalDayStart(start!!) to utcMidnightToLocalDayEnd(end!!))
            }) { Text("OK", color = Jade) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f), title = { Text(title, modifier = Modifier.padding(16.dp)) })
    }
}
