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
    val categoryId: Long,
    /** The price before the latest change, while the change is recent (see [SubscriptionDetector]). */
    val previousAmount: Double? = null,
    /** When the new price was first charged. Set together with [previousAmount]. */
    val priceChangedOn: Long? = null
) {
    val yearlyCost: Double get() = amount * cadence.perYear
    /** New price minus old: positive for a rise. */
    val priceChange: Double? get() = previousAmount?.let { amount - it }
}

/**
 * Finds repeating payments in the expense history: the same payee, a similar amount, at regular
 * intervals, and still going (the next payment isn't long overdue). A missed payment ends a run;
 * the payments after it start a new one.
 *
 * Price changes: a subscription's payments can continue at its cadence at another price (a rise
 * over 10% falls in another amount group), and the latest change is reported while it's recent
 * ([CHANGE_SHOWN_CYCLES]). The old price must have been paid as often as a new subscription
 * needs ([Cadence.minPayments]), so one odd month isn't a change.
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
    /**
     * Amounts within ₹1 or 2% of each other are the same price. Charges billed in dollars move with
     * the exchange rate every month, and that isn't a price change.
     */
    private const val SAME_PRICE_RUPEES = 1.0
    /** A change is shown for this many billing cycles from the first charge at the new price. */
    private const val CHANGE_SHOWN_CYCLES = 2
    /** More than double (or under half) the old price is another purchase or plan, not a price change. */
    private const val MAX_CHANGE_RATIO = 2.0
    /** Another purchase from the payee this many cycles before a new price makes it doubtful (see [extend]). */
    private const val OTHER_PURCHASE_CYCLES = 2

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
            .filter { it.amount > 0 && it.amount.isFinite() && it.source != "Recurring" }
            .groupBy { keyFor(it.description) }
            .filterKeys { key -> key.length >= 3 && !FALLBACK_NAMES.matches(key) && key !in dismissedKeys }
            .filterKeys { key -> bills.none { it == key || it.contains(key) || key.contains(it) } }
            .mapNotNull { (key, payments) -> best(key, payments, now) }
            .sortedByDescending { it.yearlyCost }
            .toList()
    }

    /** Payments at one cadence, oldest first; possibly at more than one price. */
    private class Run(val payments: List<Expense>, val cadence: Cadence)

    private fun best(key: String, payments: List<Expense>, now: Long): Subscription? {
        val groups = clusterByAmount(payments).map { merged(it) }
        return groups.flatMap { runs(it) }
            .map { extend(it, groups, payments) }
            .mapNotNull { subscription(key, it, now) }
            .maxByOrNull { it.payments }
    }

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

    private fun samePrice(a: Double, b: Double) = abs(a - b) <= SAME_PRICE_RUPEES || within(a, b, STRICT_AMOUNT_TOLERANCE)

    private fun days(ms: Long) = TimeUnit.MILLISECONDS.toDays(ms)

    private fun fits(a: Expense, b: Expense, cadence: Cadence) = days(b.date - a.date) in cadence.minGapDays..cadence.maxGapDays

    /** Oldest first, with the same charge seen twice (e.g. SMS and notification) kept once. */
    private fun merged(group: List<Expense>): List<Expense> =
        group.sortedBy { it.date }.fold(mutableListOf()) { acc, e ->
            if (acc.isEmpty() || days(e.date - acc.last().date) > SAME_PAYMENT_DAYS) acc += e
            acc
        }

    /**
     * The unbroken runs in one amount group: consecutive payments at one cadence. A gap that fits
     * no cadence (a missed month, an extra purchase) ends a run, so it doesn't hide the payments
     * after it for good.
     */
    private fun runs(unique: List<Expense>): List<Run> {
        val result = mutableListOf<Run>()
        var i = 0
        while (i < unique.lastIndex) {
            val cadence = Cadence.entries.firstOrNull { fits(unique[i], unique[i + 1], it) }
            if (cadence == null) { i++; continue }
            var j = i + 1
            while (j < unique.lastIndex && fits(unique[j], unique[j + 1], cadence)) j++
            qualifying(unique.subList(i, j + 1), cadence)?.let { result += it }
            i = j + 1
        }
        return result
    }

    /** [payments] as a subscription if there are enough; quarterly and yearly also need matching amounts. */
    private fun qualifying(payments: List<Expense>, cadence: Cadence): Run? {
        var kept = payments
        if (cadence.minPayments == 2) {
            // Only 2 payments are needed, so each earlier price must have been paid that often too;
            // a price paid once before (899, then 950) isn't part of it.
            val prices = byPrice(payments)
            val lastShort = prices.dropLast(1).indexOfLast { it.size < cadence.minPayments }
            kept = prices.drop(lastShort + 1).flatten()
        }
        return if (kept.size >= cadence.minPayments) Run(kept, cadence) else null
    }

    /** Splits payments into consecutive stretches at one price ([samePrice] as the stretch's first). */
    private fun byPrice(payments: List<Expense>): List<List<Expense>> {
        val prices = mutableListOf<MutableList<Expense>>()
        for (p in payments) {
            val current = prices.lastOrNull()
            if (current != null && samePrice(p.amount, current.first().amount)) current += p else prices += mutableListOf(p)
        }
        return prices
    }

    /**
     * Follows [run] into other amount groups: a payment at the cadence after its last one is the
     * next price (a rise over 10% lands in another group), with that group's run from there. Only
     * when the new price is plausible: at most double or half, and the payee had no other
     * purchases in the cycles before it (at a shop you also buy from, an order around the renewal
     * date could be taken for a new price). A one-off purchase off the cadence never counts.
     */
    private fun extend(run: Run, groups: List<List<Expense>>, all: List<Expense>): Run {
        val cadence = run.cadence
        val timeline = run.payments.toMutableList()
        while (true) {
            val last = timeline.last()
            val lastGroup = groups.first { g -> g.any { it === last } }
            val (group, next) = groups.asSequence().filter { it !== lastGroup }
                .flatMap { g -> g.asSequence().map { g to it } }
                .filter { (_, p) -> fits(last, p, cadence) && plausible(last.amount, p.amount) }
                .minByOrNull { (_, p) -> p.date } ?: break
            if (!samePrice(last.amount, next.amount) && otherPurchasesBefore(next, timeline, all, cadence)) break
            val from = group.indexOfFirst { it === next }
            var to = from
            while (to < group.lastIndex && fits(group[to], group[to + 1], cadence)) to++
            timeline += group.subList(from, to + 1)
        }
        return Run(timeline, cadence)
    }

    private fun plausible(old: Double, new: Double) = new <= old * MAX_CHANGE_RATIO && new * MAX_CHANGE_RATIO >= old

    /** Payments from the payee in the cycles before [next] that aren't the subscription (or the same charge seen twice). */
    private fun otherPurchasesBefore(next: Expense, timeline: List<Expense>, all: List<Expense>, cadence: Cadence): Boolean {
        val since = shift(next.date, cadence, -OTHER_PURCHASE_CYCLES)
        val subscription = timeline + next
        return all.any { p ->
            p.date >= since && p.date < next.date &&
                subscription.none { abs(days(p.date - it.date)) <= SAME_PAYMENT_DAYS }
        }
    }

    private fun subscription(key: String, run: Run, now: Long): Subscription? {
        val cadence = run.cadence
        val last = run.payments.last()
        val next = nextAfter(last.date, cadence)
        // Stopped: the next payment should have happened a while ago.
        if (now > next + TimeUnit.DAYS.toMillis(GRACE_DAYS.toLong())) return null

        val prices = byPrice(run.payments)
        val newPrice = prices.last()
        val oldPrice = prices.getOrNull(prices.size - 2)
        val changedOn = newPrice.first().date
        val change = oldPrice != null && oldPrice.size >= cadence.minPayments &&
            now < shift(changedOn, cadence, CHANGE_SHOWN_CYCLES)

        return Subscription(
            key = key,
            name = last.description.substringAfter("→").trim(),
            amount = last.amount,
            cadence = cadence,
            lastPaid = last.date,
            nextExpected = next,
            payments = run.payments.size,
            categoryId = run.payments.lastOrNull { it.categoryId != -1L }?.categoryId ?: -1L,
            previousAmount = if (change) oldPrice!!.last().amount else null,
            priceChangedOn = if (change) changedOn else null
        )
    }

    private fun nextAfter(date: Long, cadence: Cadence): Long = shift(date, cadence, 1)

    /** [date] moved by [cycles] of [cadence], on the calendar (so monthly keeps the day of the month). */
    private fun shift(date: Long, cadence: Cadence, cycles: Int): Long = Calendar.getInstance().apply {
        timeInMillis = date
        when (cadence) {
            Cadence.WEEKLY -> add(Calendar.DAY_OF_YEAR, 7 * cycles)
            Cadence.MONTHLY -> add(Calendar.MONTH, cycles)
            Cadence.QUARTERLY -> add(Calendar.MONTH, 3 * cycles)
            Cadence.YEARLY -> add(Calendar.YEAR, cycles)
        }
    }.timeInMillis
}
