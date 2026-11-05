package com.example.expensetracker.ui.widgets

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.example.expensetracker.widget.BudgetPulseWidgetReceiver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetUpdateHelper {
    @VisibleForTesting
    internal var request: (Context) -> Unit = BudgetPulseWidgetReceiver::requestUpdate

    /**
     * Asks the widget to refresh, off the main thread: asking Android for the widget ids is a call
     * into the system that can stall when it's busy, and on the main thread (at startup, after
     * every save) it froze the app ("Input dispatching timed out"). Suspends until it's done, so
     * an SMS receiver's refresh finishes before the receiver says it's finished. A widget that
     * can't refresh mustn't fail what asked for it.
     */
    suspend fun update(context: Context) {
        val appContext = context.applicationContext ?: context
        withContext(Dispatchers.IO) {
            try {
                request(appContext)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }
}
