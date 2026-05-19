package com.example.expensetracker.ui.widgets

import android.content.Context
import com.example.expensetracker.widget.BudgetPulseWidgetReceiver

object WidgetUpdateHelper {
    fun update(context: Context) {
        BudgetPulseWidgetReceiver.requestUpdate(context)
    }
}
