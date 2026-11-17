package com.example.expensetracker.data.period

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
