package com.example.expensetracker.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockStateTest {
    private val s = AppLockState(relockAfterMs = 30_000)

    @Test fun `starts locked when the lock is on`() = assertTrue(s.onForeground(lockEnabled = true, now = 0))

    @Test fun `never locks when the lock is off`() {
        assertFalse(s.onForeground(lockEnabled = false, now = 0))
        s.onBackground(now = 1_000)
        assertFalse(s.onForeground(lockEnabled = false, now = 999_999))
    }

    @Test fun `short trip away stays unlocked`() {
        s.unlock()
        s.onBackground(now = 10_000)
        assertFalse(s.onForeground(lockEnabled = true, now = 39_999))
    }

    @Test fun `30 seconds away locks again`() {
        s.unlock()
        s.onBackground(now = 10_000)
        assertTrue(s.onForeground(lockEnabled = true, now = 40_000))
        assertFalse(s.unlocked)
    }

    @Test fun `returning without going to the background keeps it unlocked`() {
        s.unlock()
        assertFalse(s.onForeground(lockEnabled = true, now = 1_000_000))
    }

    @Test fun `going to the background while locked stays locked`() {
        s.onBackground(now = 0)
        assertTrue(s.onForeground(lockEnabled = true, now = 1_000))
    }

    @Test fun `turning the lock on later starts locked only after a real trip away`() {
        assertFalse(s.onForeground(lockEnabled = false, now = 0)) // unlocked while off
        s.onBackground(now = 1_000)
        assertTrue(s.onForeground(lockEnabled = true, now = 60_000))
    }
}
