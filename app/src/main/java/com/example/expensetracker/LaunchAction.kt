package com.example.expensetracker

/** What a launch intent asks the app to do. OpenSubscriptions is for F5's price-change alerts. */
sealed class LaunchAction {
    data class Categorize(val expenseId: Long) : LaunchAction()
    object QuickAdd : LaunchAction()
    object OpenSubscriptions : LaunchAction()

    /** The (action, expenseId) pair [from] reads, so a pending action can go into saved state. */
    fun toExtras(): Pair<String?, Long> = when (this) {
        is Categorize -> null to expenseId
        QuickAdd -> QUICK_ADD to -1L
        OpenSubscriptions -> OPEN_SUBSCRIPTIONS to -1L
    }

    companion object {
        const val EXTRA_ACTION = "launch_action"
        const val QUICK_ADD = "quick_add"
        const val OPEN_SUBSCRIPTIONS = "open_subscriptions"

        /** Pure so it can be unit-tested: pass intent extras as plain values. */
        fun from(action: String?, expenseId: Long): LaunchAction? = when {
            expenseId != -1L -> Categorize(expenseId)
            action == QUICK_ADD -> QuickAdd
            action == OPEN_SUBSCRIPTIONS -> OpenSubscriptions
            else -> null
        }

        /**
         * For Activity.onCreate. A recreated activity (rotation, or Android restoring it) gets its
         * original intent again; acting on it twice would reopen the add dialog or Categorize. What
         * counts then is [stillPending]: an action saved because it hadn't been handled yet (it
         * arrived during onboarding, say), which must still run. Reopening from Recents
         * ([fromHistory], FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) re-delivers the task's first intent
         * too, e.g. the widget's +, which was handled back then.
         */
        fun onCreate(recreated: Boolean, action: String?, expenseId: Long, stillPending: LaunchAction? = null, fromHistory: Boolean = false): LaunchAction? = when {
            recreated -> stillPending
            fromHistory -> null
            else -> from(action, expenseId)
        }

        /**
         * A tap arriving while the app is open. One with nothing to do (the widget's body) must not
         * drop an action still waiting, such as a notification tap held behind App Lock.
         */
        fun onNewIntent(waiting: LaunchAction?, incoming: LaunchAction?): LaunchAction? = incoming ?: waiting
    }
}
