package com.example.expensetracker.ui.screens.dashboard

import com.example.expensetracker.data.db.entity.Bill

object CashFlow {
    /**
     * Bills that will still take money out this month. Reminder bills count through their due day
     * (an earlier one is assumed paid and already in spent). Auto-logged bills count until they're
     * logged, which happens on the due day.
     */
    fun billsStillDue(bills: List<Bill>, today: Int, daysInMonth: Int): Double =
        bills.filter { it.isEnabled }.filter { bill ->
            val dueDay = bill.dueDayOfMonth.coerceAtMost(daysInMonth)
            if (bill.autoLog) dueDay > today else dueDay >= today
        }.sumOf { it.amount }
}
