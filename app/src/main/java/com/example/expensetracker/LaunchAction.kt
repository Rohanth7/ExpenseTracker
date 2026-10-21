package com.example.expensetracker

/** What a launch intent asks the app to do. F5 adds OpenSubscriptions. */
sealed class LaunchAction {
    data class Categorize(val expenseId: Long) : LaunchAction()
    object QuickAdd : LaunchAction()
    object OpenSubscriptions : LaunchAction()

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
         * original intent again; acting on it twice would reopen the add dialog or Categorize.
         */
        fun onCreate(recreated: Boolean, action: String?, expenseId: Long): LaunchAction? =
            if (recreated) null else from(action, expenseId)
    }
}
