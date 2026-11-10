package com.example.expensetracker.ui.screens.dashboard

import com.example.expensetracker.data.db.entity.Loan
import java.util.Calendar

/**
 * A loan's EMIs, shared by the Overview and Settings. [Loan.startDate] is in the month of the
 * first EMI ("Months ago" = how many months back that was, 0 = this month), so the EMI due in
 * that month is EMI 1. An EMI counts as paid once its due date (day clamped to the month's
 * length, like bills) has come.
 */
object LoanProgress {
    fun paid(loan: Loan, now: Long): Int {
        val first = firstMonth(loan).timeInMillis
        if (now < first || loan.tenureMonths < 1) return 0
        return CashFlow.dueDatesInRange(loan.dueDayOfMonth, first, minOf(now, lastDueDate(loan))).size
    }

    fun remaining(loan: Loan, now: Long): Int = (loan.tenureMonths - paid(loan, now)).coerceAtLeast(0)

    /** Midnight of the last EMI's due date. */
    fun lastDueDate(loan: Loan): Long = firstMonth(loan).apply {
        add(Calendar.MONTH, loan.tenureMonths - 1)
        set(Calendar.DAY_OF_MONTH, loan.dueDayOfMonth.coerceIn(1, getActualMaximum(Calendar.DAY_OF_MONTH)))
    }.timeInMillis

    private fun firstMonth(loan: Loan) = Calendar.getInstance().apply {
        timeInMillis = loan.startDate
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
}
