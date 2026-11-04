package com.example.expensetracker.ui.widgets

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.example.expensetracker.widget.BudgetPulseWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object WidgetUpdateHelper {
    @VisibleForTesting
    internal var request: (Context) -> Unit = BudgetPulseWidgetReceiver::requestUpdate

    // One background thread, so updates keep their order. Asking Android for the widget ids is a
    // call into the system that can stall when it's busy; on the main thread (at startup, after
    // every save) it froze the app ("Input dispatching timed out").
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** Returns at once. A widget that can't refresh mustn't fail what asked for it. */
    fun update(context: Context) {
        val appContext = context.applicationContext ?: context
        scope.launch { runCatching { request(appContext) } }
    }
}
