package com.example.expensetracker.data.subscriptions

import com.example.expensetracker.data.db.entity.Expense
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.math.abs

/**
 * Every billing plan against every payment pattern the price-change rules cover (NOTES → Features →
 * Subscription price changes). Expected results come from those rules, not from the code. All
 * cells are checked and every failure reported at once.
 */
class SubscriptionScenarioTest {
    private fun date(y: Int, m: Int, d: Int): Long =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, 10, 0) }.timeInMillis

    /** 6 days into each plan's current cycle, like the 10th → the 16th. */
    private fun Plan.now(): Long = at(0, days = 6)

    /** A billing plan: its price, its cadence, and where its cycles fall (cycle 0 is the latest, before [now]). */
    private enum class Plan(val label: String, val price: Double, val cadence: Cadence, val halfCycleDays: Int) {
        WEEKLY("weekly", 99.0, Cadence.WEEKLY, 3),
        DAYS_28("28-day", 239.0, Cadence.MONTHLY, 14),
        MONTHLY("monthly", 649.0, Cadence.MONTHLY, 15),
        MONTH_END("month-end", 499.0, Cadence.MONTHLY, 15),
        QUARTERLY("quarterly", 1499.0, Cadence.QUARTERLY, 45),
        YEARLY("yearly", 899.0, Cadence.YEARLY, 180);

        val m get() = cadence.minPayments
    }

    private fun Plan.at(cycle: Int, days: Int = 0): Long = Calendar.getInstance().apply {
        clear()
        when (this@at) {
            Plan.WEEKLY -> { set(2026, 9, 10, 10, 0); add(Calendar.DAY_OF_YEAR, 7 * cycle) }
            Plan.DAYS_28 -> { set(2026, 9, 10, 10, 0); add(Calendar.DAY_OF_YEAR, 28 * cycle) }
            Plan.MONTHLY -> { set(2026, 9, 10, 10, 0); add(Calendar.MONTH, cycle) }
            // The last day of each month: 31 Aug, 30 Sep, 31 Oct
            Plan.MONTH_END -> { set(2026, 9, 1, 10, 0); add(Calendar.MONTH, cycle); set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)) }
            Plan.QUARTERLY -> { set(2026, 9, 10, 10, 0); add(Calendar.MONTH, 3 * cycle) }
            Plan.YEARLY -> { set(2026, 9, 10, 10, 0); add(Calendar.YEAR, cycle) }
        }
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

    private fun pay(amount: Double, date: Long, desc: String = "Payee", source: String = "SMS") =
        Expense(amount = Math.round(amount * 100) / 100.0, description = desc, date = date, source = source, categoryId = 4)

    private fun Plan.paid(cycles: IntRange, price: Double = this.price) = cycles.map { pay(price, at(it)) }

    /** What the detector should report; null fields aren't checked. */
    private data class Expect(
        val listed: Boolean?,
        val change: Boolean? = null,
        val rose: Boolean? = null,
        val previous: Double? = null,
        val amount: Double? = null,
        val payments: Int? = null
    )

    private class Scenario(val name: String, val build: (Plan) -> List<Expense>, val expect: (Plan) -> Expect)

    private val scenarios = listOf(
        Scenario("steady price", { p -> p.paid(-(p.m - 1)..0) },
            { p -> Expect(listed = true, change = false, amount = p.price, payments = p.m) }),
        Scenario("rise of 20%", { p -> p.paid(-p.m..-1) + pay(p.price * 1.2, p.at(0)) },
            { p -> Expect(listed = true, change = true, rose = true, previous = p.price, amount = p.price * 1.2) }),
        Scenario("rise of 6%", { p -> p.paid(-p.m..-1) + pay(p.price * 1.06, p.at(0)) },
            { p -> Expect(listed = true, change = true, rose = true, previous = p.price, amount = p.price * 1.06) }),
        Scenario("drop of 20%", { p -> p.paid(-p.m..-1) + pay(p.price * 0.8, p.at(0)) },
            { p -> Expect(listed = true, change = true, rose = false, previous = p.price, amount = p.price * 0.8) }),
        Scenario("change of 1.5%", { p -> p.paid(-p.m..-1) + pay(p.price * 1.015, p.at(0)) },
            { _ -> Expect(listed = true, change = false) }),
        Scenario("dollar charge drifting with the rate", { p ->
            val drift = listOf(1.0, 1.015, 1.037, 1.02, 1.045, 1.03)
            (-p.m..0).mapIndexed { i, c -> pay((p.price + 0.37) * drift[i], p.at(c)) }
        }, { p -> if (p == Plan.WEEKLY) Expect(listed = null, change = false) else Expect(listed = true, change = false) }),
        Scenario("new price seen by SMS and payment app", { p ->
            p.paid(-p.m..-1) + pay(p.price * 1.2, p.at(0)) + pay(p.price * 1.2, p.at(0), desc = "GPay → Payee", source = "UPI")
        }, { p -> Expect(listed = true, change = true, previous = p.price, payments = p.m + 1) }),
        Scenario("missed a cycle, then a new price", { p -> p.paid(-(p.m + 1)..-2) + pay(p.price * 1.2, p.at(0)) },
            { _ -> Expect(listed = null, change = false) }),
        Scenario("stopped", { p -> p.paid(-(p.m + 1)..-2) },
            { _ -> Expect(listed = false) }),
        Scenario("second subscription a day apart", { p ->
            p.paid(-(p.m - 1)..0) + (-(p.m - 1)..0).map { pay(p.price * 1.32, p.at(it, days = 1)) }
        }, { _ -> Expect(listed = true, change = false) }),
        Scenario("one-off purchase mid-cycle", { p ->
            p.paid(-(p.m - 1)..0) + pay(p.price * 1.5, p.at(-1, days = p.halfCycleDays))
        }, { p -> Expect(listed = true, change = false, amount = p.price) }),
        Scenario("one-off purchase 4 days before renewal", { p ->
            p.paid(-(p.m - 1)..0) + pay(p.price * 1.5, p.at(0, days = -4))
        }, { p -> Expect(listed = true, change = false, amount = p.price) }),
        Scenario("another purchase before a rise", { p ->
            p.paid(-p.m..-1) + pay(p.price * 1.5, p.at(-1, days = p.halfCycleDays)) + pay(p.price * 1.2, p.at(0))
        }, { _ -> Expect(listed = true, change = false) }),
        Scenario("another purchase before a 6% rise", { p ->
            p.paid(-p.m..-1) + pay(p.price * 1.5, p.at(-1, days = p.halfCycleDays)) + pay(p.price * 1.06, p.at(0))
        }, { _ -> Expect(listed = true, change = false) }),
        Scenario("new price 4 days before renewal", { p -> p.paid(-p.m..-1) + pay(p.price * 1.2, p.at(0, days = -4)) },
            { _ -> Expect(listed = true, change = false) }),
        Scenario("new price a day early", { p -> p.paid(-p.m..-1) + pay(p.price * 1.2, p.at(0, days = -1)) },
            { p -> Expect(listed = true, change = true, previous = p.price) }),
        Scenario("new price a day late", { p -> p.paid(-p.m..-1) + pay(p.price * 1.2, p.at(0, days = 1)) },
            { p -> Expect(listed = true, change = true, previous = p.price) }),
        // Weekly payments are 6–8 days apart (#8): 9 days isn't the next week's, at any price
        Scenario("new price two days late", { p -> p.paid(-p.m..-1) + pay(p.price * 1.2, p.at(0, days = 2)) },
            { p -> if (p == Plan.WEEKLY) Expect(listed = null, change = false) else Expect(listed = true, change = true, previous = p.price) }),
        Scenario("more than doubled (another plan)", { p -> p.paid(-p.m..-1) + pay(p.price * 2.5, p.at(0)) },
            { _ -> Expect(listed = null, change = false) }),
        Scenario("old price paid too few times", { p -> p.paid(-(p.m - 1)..-1) + pay(p.price * 1.2, p.at(0)) },
            { _ -> Expect(listed = null, change = false) }),
        Scenario("second cycle at the new price", { p -> p.paid(-(p.m + 1)..-2) + p.paid(-1..0, p.price * 1.2) },
            { p -> Expect(listed = true, change = true, previous = p.price, amount = p.price * 1.2) }),
        Scenario("change three cycles ago", { p -> p.paid(-(p.m + 2)..-3) + p.paid(-2..0, p.price * 1.2) },
            { p -> Expect(listed = true, change = false, amount = p.price * 1.2) }),
        Scenario("price went back after one cycle", { p -> p.paid(-(p.m + 1)..-2) + pay(p.price * 1.5, p.at(-1)) + pay(p.price, p.at(0)) },
            { _ -> Expect(listed = null, change = false) }),
    )

    private fun close(a: Double, b: Double) = abs(a - b) < 0.01

    private fun check(e: Expect, s: Subscription?): String? {
        if (e.listed == false) return s?.let { "listed (${it.amount}, ${it.cadence})" }
        if (s == null) return if (e.listed == true) "not listed" else null
        val wrong = mutableListOf<String>()
        e.change?.let { if ((s.previousAmount != null) != it) wrong += if (it) "no change reported" else "change ${s.previousAmount} → ${s.amount} reported" }
        e.rose?.let { if (s.previousAmount != null && s.priceRose != it) wrong += "priceRose=${s.priceRose}" }
        e.previous?.let { if (s.previousAmount != null && !close(s.previousAmount!!, it)) wrong += "previous ${s.previousAmount}, expected $it" }
        e.amount?.let { if (!close(s.amount, Math.round(it * 100) / 100.0)) wrong += "amount ${s.amount}, expected ${Math.round(it * 100) / 100.0}" }
        e.payments?.let { if (s.payments != it) wrong += "${s.payments} payments, expected $it" }
        return wrong.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    @Test fun `every plan against every payment pattern`() {
        val failures = mutableListOf<String>()
        val grid = StringBuilder("%-40s".format("") + Plan.entries.joinToString("") { "%-11s".format(it.label) } + "\n")
        for (scenario in scenarios) {
            grid.append("%-40s".format(scenario.name))
            for (plan in Plan.entries) {
                val found = SubscriptionDetector.detect(scenario.build(plan), plan.now())
                val problem = check(scenario.expect(plan), found.singleOrNull()) ?: if (found.size > 1) "${found.size} subscriptions" else null
                grid.append("%-11s".format(if (problem == null) "ok" else "FAIL"))
                if (problem != null) failures += "${plan.label} / ${scenario.name}: $problem"
            }
            grid.append("\n")
        }
        val cells = scenarios.size * Plan.entries.size
        println(grid)
        println("${cells - failures.size}/$cells pass")
        assertTrue("${failures.size} of $cells failed:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
