package com.example.expensetracker.ui.screens.dashboard

import com.example.expensetracker.data.period.BudgetPeriod
import com.example.expensetracker.data.period.DateRange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/** One row of Home's "Coming up": a bill reminder or a loan's next EMI. [emi] is "15 of 36" for an EMI. */
data class ComingUpItem(val name: String, val amount: Double, val dueDate: Long, val daysUntilDue: Int, val emi: String? = null)

object ComingUp {
    /**
     * [bills] as the Overview already picked them, and each of [loans]' EMIs still due in [cycle]
     * (the ones [CashFlow.emiStillDue] counts), soonest first.
     */
    fun of(bills: List<UpcomingBill>, loans: List<LoanStatus>, now: Long, cycle: DateRange): List<ComingUpItem> {
        val todayStart = BudgetPeriod.startOfDay(now)
        fun daysFromToday(n: Int) = Calendar.getInstance().apply { timeInMillis = todayStart; add(Calendar.DAY_OF_YEAR, n) }.timeInMillis
        val billItems = bills.map { ComingUpItem(it.bill.name, it.bill.amount, daysFromToday(it.daysUntilDue), it.daysUntilDue) }
        val emiItems = loans.mapNotNull { status ->
            val loan = status.loan
            val from = maxOf(todayStart, BudgetPeriod.startOfDay(loan.startDate))
            val to = minOf(cycle.endInclusive, LoanProgress.lastDueDate(loan))
            val due = (if (from > to) emptyList() else CashFlow.dueDatesInRange(loan.dueDayOfMonth, from, to)).firstOrNull()
                ?: return@mapNotNull null
            ComingUpItem(
                "${loan.name} EMI", loan.monthlyEmi, due, ((due - todayStart) / DAY_MS.toDouble()).roundToInt(),
                emi = "${LoanProgress.paid(loan, due)} of ${loan.tenureMonths}"
            )
        }
        return (billItems + emiItems).sortedBy { it.dueDate }
    }

    /** "Today", "in 3 days", or the date from four days out; then the EMI's number. */
    fun subtitle(item: ComingUpItem, locale: Locale = Locale.ENGLISH): String {
        val due = when (item.daysUntilDue) {
            -1 -> "Yesterday"
            0 -> "Today"
            1 -> "Tomorrow"
            2, 3 -> "in ${item.daysUntilDue} days"
            else -> SimpleDateFormat("d MMM", locale).format(item.dueDate)
        }
        return if (item.emi != null) "$due · ${item.emi}" else due
    }

    private const val DAY_MS = 24 * 60 * 60 * 1000L
}
