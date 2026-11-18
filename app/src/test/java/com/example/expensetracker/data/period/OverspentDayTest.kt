package com.example.expensetracker.data.period

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class OverspentDayTest {
    private class MemoryStore : OverspentDay.Store {
        val days = mutableMapOf<String, Long>()
        override fun all() = days.toMap()
        override fun put(key: String, epochDay: Long) { days[key] = epochDay }
        override fun remove(keys: Collection<String>) { days.keys.removeAll(keys.toSet()) }
    }

    private val store = MemoryStore()
    private val overspent = OverspentDay(store)
    private val november = 20_758L  // 1 Nov 2026

    @Test fun theDayTheMoneyRunsOutIsDayZero() {
        assertEquals(0, overspent.daysOver(-500.0, november, november + 16))
    }

    @Test fun theNextDayIsOne() {
        overspent.daysOver(-500.0, november, november + 16)
        assertEquals(1, overspent.daysOver(-800.0, november, november + 17))
    }

    @Test fun backUnderTheLimitForgetsTheDay() {
        overspent.daysOver(-500.0, november, november + 16)
        assertEquals(0, overspent.daysOver(0.0, november, november + 17))
        assertTrue(store.days.isEmpty())
        // Over again later: that day is the new day zero.
        assertEquals(0, overspent.daysOver(-10.0, november, november + 20))
        assertEquals(1, overspent.daysOver(-10.0, november, november + 21))
    }

    @Test fun aNewMonthStartsClean() {
        overspent.daysOver(-500.0, november, november + 16)
        val december = november + 30
        assertEquals(0, overspent.daysOver(-100.0, december, december))
        assertEquals(setOf(OverspentDay.PREFIX + december), store.days.keys)
    }

    @Test fun aNewMonthThatIsNotOverKeepsNothing() {
        overspent.daysOver(-500.0, november, november + 16)
        assertEquals(0, overspent.daysOver(1_000.0, november + 30, november + 30))
        assertTrue(store.days.isEmpty())
    }

    // record(): the one entry point Home's view model and the widget use.

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12) =
        Calendar.getInstance().apply { clear(); set(year, month - 1, day, hour, 0) }.timeInMillis

    private fun record(now: Long, spent: Double, income: Double = 30_000.0, stillDue: Double = 0.0, startDay: Int = 1) =
        overspent.record(income, spent, stillDue, now, startDay)

    @Test fun recordsTheDayItIsFirstSeenOver() {
        assertEquals(0, record(at(2026, 11, 5), spent = 31_000.0))
        assertEquals(mapOf(OverspentDay.PREFIX + november to november + 4), store.days)
    }

    @Test fun laterDaysCountFromTheRecordedDayWithoutRecordingAgain() {
        record(at(2026, 11, 5), spent = 31_000.0)
        assertEquals(7, record(at(2026, 11, 12), spent = 32_000.0))
        assertEquals(mapOf(OverspentDay.PREFIX + november to november + 4), store.days)
    }

    @Test fun stillDueCountsAgainstFreeToSpend() {
        assertEquals(0, record(at(2026, 11, 5), spent = 25_000.0, stillDue = 6_000.0))
        assertEquals(1, record(at(2026, 11, 6), spent = 25_000.0, stillDue = 6_000.0))
    }

    @Test fun backToZeroOrMoreClearsIt() {
        record(at(2026, 11, 5), spent = 31_000.0)
        assertEquals(0, record(at(2026, 11, 6), spent = 30_000.0))
        assertTrue(store.days.isEmpty())
    }

    @Test fun aNewMonthStartsCleanFromItsOwnStartDay() {
        record(at(2026, 11, 26), spent = 31_000.0, startDay = 25)
        assertEquals(0, record(at(2026, 12, 25), spent = 31_000.0, startDay = 25))
        assertEquals(1, record(at(2026, 12, 26), spent = 31_000.0, startDay = 25))
        assertEquals(setOf(OverspentDay.PREFIX + BudgetPeriod.epochDay(at(2026, 12, 25))), store.days.keys)
    }

    @Test fun withoutAnIncomeNothingRunsOut() {
        assertEquals(0, record(at(2026, 11, 17), income = 0.0, spent = 60_000.0))
        assertTrue(store.days.isEmpty())
    }
}
