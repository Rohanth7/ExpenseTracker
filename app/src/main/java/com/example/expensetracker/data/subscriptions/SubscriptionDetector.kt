package com.example.expensetracker.data.subscriptions

import com.example.expensetracker.data.db.entity.Expense
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.abs

enum class Cadence(val label: String, val minGapDays: Int, val maxGapDays: Int, val perYear: Int, val minPayments: Int) {
    WEEKLY("Weekly", 6, 8, 52, 4),
    MONTHLY("Monthly", 26, 35, 12, 3),
    QUARTERLY("Every 3 months", 84, 98, 4, 2),
    YEARLY("Yearly", 350, 380, 1, 2);
}

data class Subscription(
    /** Stable id for this payee, used to remember "not a subscription". */
    val key: String,
    val name: String,
    /** The most recent payment. */
    val amount: Double,
    val cadence: Cadence,
    val lastPaid: Long,
    val nextExpected: Long,
    val payments: Int,
    val categoryId: Long
) {
    val yearlyCost: Double get() = amount * cadence.perYear
}

/**
 * Finds repeating payments in the expense history: the same payee, a similar amount, at regular
 * intervals, and still going (the next payment isn't long overdue).
 *
 * Pure Kotlin so it can be unit-tested; [now] is passed in.
 */
object SubscriptionDetector {
    /** Amounts within 10% count as the same subscription, so a small price rise doesn't break it. */
    private const val AMOUNT_TOLERANCE = 0.10
    /** Quarterly and yearly need only 2 payments, so their amounts must match closely. */
    private const val STRICT_AMOUNT_TOLERANCE = 0.02
    /** Payments this close together are the same charge seen twice. */
    private const val SAME_PAYMENT_DAYS = 2
    /** A subscription stays listed for this long after its payment was due. */
    private const val GRACE_DAYS = 10

    private val FALLBACK_NAMES = Regex("""^(bank debit|.+ debit|upi|gpay|phonepe|paytm|bhim|amazon pay|cred)$""")

    /** "GPay → Netflix" and "NETFLIX" both become "netflix". */
    fun keyFor(description: String): String =
        description.substringAfter("→").trim().lowercase().replace(Regex("""\s+"""), " ")

    fun detect(
        expenses: List<Expense>,
        now: Long,
        dismissedKeys: Set<String> = emptySet(),
        billNames: Collection<String> = emptyList()
    ): List<Subscription> {
        val bills = billNames.map { keyFor(it) }.filter { it.isNotEmpty() }
        return expenses
            .asSequence()
            // Bills are already tracked; "Recurring" entries are the app's own auto-logged bills.
            .filter { it.amount > 0 && it.source != "Recurring" }
            .groupBy { keyFor(it.description) }
            .filterKeys { key -> key.length >= 3 && !FALLBACK_NAMES.matches(key) && key !in dismissedKeys }
            .filterKeys { key -> bills.none { it == key || it.contains(key) || key.contains(it) } }
            .mapNotNull { (key, payments) -> best(key, payments, now) }
            .sortedByDescending { it.yearlyCost }
            .toList()
    }

    private fun best(key: String, payments: List<Expense>, now: Long): Subscription? =
        clusterByAmount(payments).mapNotNull { fit(key, it, now) }.maxByOrNull { it.payments }

    /** Groups payments whose amounts are within [AMOUNT_TOLERANCE] of the group's first amount. */
    private fun clusterByAmount(payments: List<Expense>): List<List<Expense>> {
        val clusters = mutableListOf<MutableList<Expense>>()
        for (p in payments.sortedBy { it.amount }) {
            val c = clusters.lastOrNull()
            if (c != null && within(p.amount, c.first().amount, AMOUNT_TOLERANCE)) c += p else clusters += mutableListOf(p)
        }
        return clusters
    }

    private fun within(a: Double, b: Double, tolerance: Double) = abs(a - b) <= tolerance * maxOf(a, b)

    private fun days(ms: Long) = TimeUnit.MILLISECONDS.toDays(ms)

    private fun fit(key: String, cluster: List<Expense>, now: Long): Subscription? {
        // Merge the same charge seen twice (e.g. SMS and notification) into one payment.
        val sorted = cluster.sortedBy { it.date }
        val unique = sorted.fold(mutableListOf<Expense>()) { acc, e ->
            if (acc.isEmpty() || days(e.date - acc.last().date) > SAME_PAYMENT_DAYS) acc += e
            acc
        }
        if (unique.size < 2) return null
        val gaps = unique.zipWithNext { a, b -> days(b.date - a.date) }
        val cadence = Cadence.entries.firstOrNull { c -> gaps.all { it in c.minGapDays..c.maxGapDays } } ?: return null
        if (unique.size < cadence.minPayments) return null
        if (cadence.minPayments == 2 && !within(unique.first().amount, unique.last().amount, STRICT_AMOUNT_TOLERANCE)) return null

        val last = unique.last()
        val next = nextAfter(last.date, cadence)
        // Stopped: the next payment should have happened a while ago.
        if (now > next + TimeUnit.DAYS.toMillis(GRACE_DAYS.toLong())) return null

        return Subscription(
            key = key,
            name = last.description.substringAfter("→").trim(),
            amount = last.amount,
            cadence = cadence,
            lastPaid = last.date,
            nextExpected = next,
            payments = unique.size,
            categoryId = unique.lastOrNull { it.categoryId != -1L }?.categoryId ?: -1L
        )
    }

    private fun nextAfter(date: Long, cadence: Cadence): Long = Calendar.getInstance().apply {
        timeInMillis = date
        when (cadence) {
            Cadence.WEEKLY -> add(Calendar.DAY_OF_YEAR, 7)
            Cadence.MONTHLY -> add(Calendar.MONTH, 1)
            Cadence.QUARTERLY -> add(Calendar.MONTH, 3)
            Cadence.YEARLY -> add(Calendar.YEAR, 1)
        }
    }.timeInMillis
}
