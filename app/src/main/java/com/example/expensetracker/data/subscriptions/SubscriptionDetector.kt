package com.example.expensetracker.data.subscriptions

import com.example.expensetracker.data.db.entity.Expense
import java.util.Calendar
import java.util.IdentityHashMap
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
    val priceRose: Boolean get() = (priceChange ?: 0.0) > 0
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
    /** A monthly or longer charge converted from another currency (it has paise) can move this much with the exchange rate. */
    private const val CONVERTED_AMOUNT_TOLERANCE = 0.05
    /** Payments this close together are the same charge seen twice. */
    private const val SAME_PAYMENT_DAYS = 2
    /** A subscription stays listed for this long after its payment was due. */
    private const val GRACE_DAYS = 10
    /**
     * Amounts within ₹1 or 2% of each other are the same price (5% for monthly and longer charges
     * with paise). Charges billed in dollars move with the exchange rate every month, and that isn't
     * a price change; weekly charges with paise are shopping.
     */
    private const val SAME_PRICE_RUPEES = 1.0
    /** A change is shown for this many billing cycles from the first charge at the new price. */
    private const val CHANGE_SHOWN_CYCLES = 2
    /** More than double (or under half) the old price is another purchase or plan, not a price change. */
    private const val MAX_CHANGE_RATIO = 2.0
    /** A new price is charged on its renewal date or later (bank SMS can be a day off); see [extend]. */
    private val EARLY_MS = TimeUnit.DAYS.toMillis(SAME_PAYMENT_DAYS.toLong())
    /** Another purchase from the payee this many cycles before a new price makes it doubtful (see [extend]). */
    private const val OTHER_PURCHASE_CYCLES = 2

    private val FALLBACK_NAMES = Regex("""^(bank debit|.+ debit|upi|gpay|phonepe|paytm|bhim|amazon pay|cred)$""")

    private val SPACES = Regex("""\s+""")

    /** "GPay → Netflix" and "NETFLIX" both become "netflix". */
    fun keyFor(description: String): String =
        description.substringAfter("→").trim().lowercase().replace(SPACES, " ")

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

    /** Payments by date, so those in a time window are found without going through them all. */
    private class ByDate(payments: List<Expense>) {
        private val sorted = payments.sortedBy { it.date }
        private val dates = LongArray(sorted.size) { sorted[it].date }

        /** Payments dated from [from] up to, not including, [until]. */
        fun between(from: Long, until: Long): List<Expense> {
            val start = firstFrom(from)
            return sorted.subList(start, maxOf(start, firstFrom(until)))
        }

        private fun firstFrom(date: Long): Int {
            var lo = 0
            var hi = dates.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (dates[mid] < date) lo = mid + 1 else hi = mid
            }
            return lo
        }
    }

    private fun best(key: String, payments: List<Expense>, now: Long): Subscription? {
        val groups = clusterByAmount(payments).map { merged(it) }
        val groupOf = IdentityHashMap<Expense, List<Expense>>().apply { groups.forEach { g -> g.forEach { put(it, g) } } }
        val grouped = ByDate(groups.flatten())
        val all = ByDate(payments)
        return groups.flatMap { runs(it) }
            .map { extend(it, grouped, groupOf, all) }
            .mapNotNull { subscription(key, it, all, now) }
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

    private fun samePrice(a: Double, b: Double, cadence: Cadence) = abs(a - b) <= SAME_PRICE_RUPEES ||
        within(a, b, if (cadence != Cadence.WEEKLY && (converted(a) || converted(b))) CONVERTED_AMOUNT_TOLERANCE else STRICT_AMOUNT_TOLERANCE)

    /** The same price for finding a subscription, which needs only 2 quarterly or yearly payments: no exchange-rate allowance. */
    private fun matches(a: Double, b: Double) = abs(a - b) <= SAME_PRICE_RUPEES || within(a, b, STRICT_AMOUNT_TOLERANCE)

    /** Rupee prices are whole rupees; a charge with paise was converted from another currency. */
    private fun converted(amount: Double) = abs(amount - Math.round(amount)) >= 0.005

    private fun days(ms: Long) = TimeUnit.MILLISECONDS.toDays(ms)

    private fun fits(a: Expense, b: Expense, cadence: Cadence) = days(b.date - a.date) in cadence.minGapDays..cadence.maxGapDays

    /** Oldest first, with the same charge seen twice (e.g. SMS and notification) kept once. */
    private fun merged(group: List<Expense>): List<Expense> =
        group.sortedBy { it.date }.fold(mutableListOf()) { acc, e ->
            if (acc.isEmpty() || days(e.date - acc.last().date) > SAME_PAYMENT_DAYS) acc += e
            acc
        }

    /**
     * The unbroken runs in one amount group: consecutive payments at one cadence. A gap that doesn't
     * fit it (a missed month, an extra purchase) ends a run, so it doesn't hide the payments after
     * it for good.
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
            // The last payment may start a run at another cadence (monthly, then quarterly).
            i = j
        }
        return result
    }

    /** [payments] as a subscription if there are enough; quarterly and yearly also need matching amounts. */
    private fun qualifying(payments: List<Expense>, cadence: Cadence): Run? {
        var kept = payments
        if (cadence.minPayments == 2) {
            // Only 2 payments are needed, so each earlier price must have been paid that often too;
            // a price paid once before (899, then 950) isn't part of it.
            val prices = byPrice(payments, ::matches)
            val lastShort = prices.dropLast(1).indexOfLast { it.size < cadence.minPayments }
            kept = prices.drop(lastShort + 1).flatten()
        }
        return if (kept.size >= cadence.minPayments) Run(kept, cadence) else null
    }

    /**
     * Splits payments into consecutive stretches at one price: each [same] as the payment before
     * it, so a charge that drifts a little every month (exchange rate) stays one price.
     */
    private fun byPrice(payments: List<Expense>, same: (Double, Double) -> Boolean): List<List<Expense>> {
        val prices = mutableListOf<MutableList<Expense>>()
        for (p in payments) {
            val current = prices.lastOrNull()
            if (current != null && same(p.amount, current.last().amount)) current += p else prices += mutableListOf(p)
        }
        return prices
    }

    /**
     * Follows [run] into other amount groups: a payment at the cadence after its last one is the
     * next price (a rise over 10% lands in another group), with that group's run from there. Only
     * when the new price is plausible: at most double or half, charged no earlier than 2 days before
     * the renewal date, and the payee had no other purchases in the cycles before it (at a shop you
     * also buy from, an order around the renewal date could be taken for a new price). A one-off
     * purchase off the cadence never counts.
     */
    private fun extend(run: Run, grouped: ByDate, groupOf: Map<Expense, List<Expense>>, all: ByDate): Run {
        val cadence = run.cadence
        val timeline = run.payments.toMutableList()
        while (true) {
            val last = timeline.last()
            val lastGroup = groupOf.getValue(last)
            val next = grouped.between(last.date + TimeUnit.DAYS.toMillis(cadence.minGapDays.toLong()),
                    last.date + TimeUnit.DAYS.toMillis(cadence.maxGapDays + 1L))
                .firstOrNull { p ->
                    groupOf.getValue(p) !== lastGroup && plausible(last.amount, p.amount) &&
                        (samePrice(last.amount, p.amount, cadence) || renewal(last, p, cadence))
                } ?: break
            if (!samePrice(last.amount, next.amount, cadence) && otherPurchasesBefore(next, timeline, all, cadence)) break
            val group = groupOf.getValue(next)
            val from = group.indexOfFirst { it === next }
            var to = from
            while (to < group.lastIndex && fits(group[to], group[to + 1], cadence)) to++
            timeline += group.subList(from, to + 1)
        }
        return Run(timeline, cadence)
    }

    private fun plausible(old: Double, new: Double) = new <= old * MAX_CHANGE_RATIO && new * MAX_CHANGE_RATIO >= old

    /** [next] is charged on [last]'s renewal date or after it (bank SMS can be a day or two off). */
    private fun renewal(last: Expense, next: Expense, cadence: Cadence) = next.date >= nextAfter(last.date, cadence) - EARLY_MS

    /**
     * Payments from the payee in the cycles before [next] that aren't the subscription. One within
     * 2 days of a subscription payment at the same price is that charge seen twice; at another
     * price it's something else (a second subscription from the payee billed a day apart).
     */
    private fun otherPurchasesBefore(next: Expense, timeline: List<Expense>, all: ByDate, cadence: Cadence): Boolean {
        val since = shift(next.date, cadence, -OTHER_PURCHASE_CYCLES)
        val near = (timeline + next).filter { it.date >= since - EARLY_MS && it.date <= next.date }
        return all.between(since, next.date).any { p ->
            near.none { abs(days(p.date - it.date)) <= SAME_PAYMENT_DAYS && samePrice(p.amount, it.amount, cadence) }
        }
    }

    private fun subscription(key: String, run: Run, all: ByDate, now: Long): Subscription? {
        val cadence = run.cadence
        val prices = byPrice(run.payments) { a, b -> samePrice(a, b, cadence) }
        val newPrice = prices.last()
        val oldPrice = prices.getOrNull(prices.size - 2)
        // A change within one amount group gets the checks [extend] makes across groups
        val changed = oldPrice != null && oldPrice.size >= cadence.minPayments
        val believable = changed && renewal(oldPrice!!.last(), newPrice.first(), cadence) &&
            !otherPurchasesBefore(newPrice.first(), run.payments, all, cadence)
        // A single payment that fails them is another purchase: leave it out while the subscription
        // is current without it. Otherwise keep every payment and report no change.
        if (changed && !believable && newPrice.size == 1)
            subscription(key, Run(run.payments.dropLast(1), cadence), all, now)?.let { return it }

        val last = run.payments.last()
        val next = nextAfter(last.date, cadence)
        // Stopped: the next payment should have happened a while ago.
        if (now > next + TimeUnit.DAYS.toMillis(GRACE_DAYS.toLong())) return null

        val changedOn = newPrice.first().date
        val change = believable && now < shift(changedOn, cadence, CHANGE_SHOWN_CYCLES)

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
