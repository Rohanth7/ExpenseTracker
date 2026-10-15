package com.example.expensetracker.security

/**
 * When the app lock should be showing. Lives for the whole process, so rotating the screen or
 * recreating the activity doesn't ask again, while a new process (after Android kills the app)
 * starts locked.
 *
 * Time is passed in so the rules can be unit-tested.
 */
class AppLockState(private val relockAfterMs: Long = RELOCK_AFTER_MS) {
    var unlocked: Boolean = false
        private set
    private var backgroundedAt: Long? = null

    fun unlock() { unlocked = true; backgroundedAt = null }

    fun lock() { unlocked = false; backgroundedAt = null }

    /** The app went to the background (not a rotation). */
    fun onBackground(now: Long) { if (unlocked) backgroundedAt = now }

    /**
     * The app came back. Returns true if it must be locked: the lock is on and the user either
     * never unlocked in this process or was away for longer than [relockAfterMs].
     */
    fun onForeground(lockEnabled: Boolean, now: Long): Boolean {
        if (!lockEnabled) { unlock(); return false }
        val away = backgroundedAt?.let { now - it }
        backgroundedAt = null
        if (unlocked && away != null && away >= relockAfterMs) unlocked = false
        return !unlocked
    }

    companion object { const val RELOCK_AFTER_MS = 30_000L }
}

object AppLock { val state = AppLockState() }
