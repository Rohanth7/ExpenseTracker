package com.example.expensetracker.ui.widgets

import android.content.Context
import androidx.glance.appwidget.updateAll

object WidgetUpdateHelper {
    suspend fun update(context: Context) {
        ExpenseWidget().updateAll(context)
    }
}
