package com.example.expensetracker.ui.screens.dashboard

import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.period.DateRange
import java.util.Calendar

/**
 * What still has to go out before the budget month ends. Bills and EMIs keep their calendar due
 * days; only the window they're counted in follows the month start (see `BudgetPeriod`).
 */
object CashFlow {
    /** Midnight of each due date (day clamped to the month's length) between [from] and [to]. */
    fun dueDatesInRange(dueDayOfMonth: Int, from: Long, to: Long): List<Long> {
        val out = mutableListOf<Long>()
        val cal = Calendar.getInstance().apply { timeInMillis = from; set(Calendar.DAY_OF_MONTH, 1) }
        repeat(3) {  // a cycle touches at most 2 calendar months; 3 is a safe bound
            val due = (cal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, dueDayOfMonth.coerceIn(1, getActualMaximum(Calendar.DAY_OF_MONTH)))
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            if (due in from..to) out += due
            cal.add(Calendar.MONTH, 1)
        }
        return out
    }

    /** Reminder bills count from today's start; auto-logged bills only after today (today's is logged and in spent). */
    fun billsStillDue(bills: List<Bill>, now: Long, cycle: DateRange): Double {
        val todayStart = startOfDay(now)
        val tomorrowStart = todayStart + 24 * 60 * 60 * 1000L
        return bills.filter { it.isEnabled }.sumOf { b ->
            val from = if (b.autoLog) tomorrowStart else todayStart
            if (from > cycle.endInclusive) 0.0 else dueDatesInRange(b.dueDayOfMonth, from, cycle.endInclusive).size * b.amount
        }
    }

    /** EMIs still to come this cycle; pass loans that still have EMIs left. Loans that start after the cycle don't count. */
    fun emiStillDue(loans: List<Loan>, now: Long, cycle: DateRange): Double {
        val todayStart = startOfDay(now)
        return loans.filter { it.isActive && it.startDate <= cycle.endInclusive }
            .sumOf { dueDatesInRange(it.dueDayOfMonth, todayStart, cycle.endInclusive).size * it.monthlyEmi }
    }

    private fun startOfDay(t: Long) = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
