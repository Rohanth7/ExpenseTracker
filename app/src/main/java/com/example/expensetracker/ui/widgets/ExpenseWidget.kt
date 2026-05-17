package com.example.expensetracker.ui.widgets

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.preferences.PreferencesManager
import java.text.NumberFormat
import java.util.*

class ExpenseWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val db = AppDatabase.getInstance(context)
            val prefs = PreferencesManager(context)
            
            val cal = Calendar.getInstance()
            val start = cal.apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val end = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
            }.timeInMillis

            val totalSpentState = androidx.compose.runtime.produceState(initialValue = 0.0) {
                value = db.expenseDao().getTotalSpentInRange(start, end)
            }
            
            WidgetContent(totalSpentState.value, prefs.monthlyIncome, prefs.widgetEnabled, prefs.privacyMode)
        }
    }

    @androidx.compose.runtime.Composable
    private fun WidgetContent(spent: Double, income: Double, enabled: Boolean, privacyMode: Boolean) {
        val canvasColor = Color(0xFF211F1B)
        val inkColor = Color(0xFFF2EBD9)
        val mutedColor = Color(0xFF8A8276)
        val jadeColor = Color(0xFF7DC9A5)
        val coralColor = Color(0xFFEE9A6E)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(canvasColor)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "OVERVIEW",
                style = TextStyle(
                    color = ColorProvider(mutedColor),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            
            Spacer(modifier = GlanceModifier.height(4.dp))
            
            if (!enabled) {
                Text(
                    text = "Widget Disabled",
                    style = TextStyle(color = ColorProvider(inkColor), fontSize = 14.sp)
                )
            } else {
                val displayText = if (privacyMode) "₹ ••••" else "₹${fmtINR(spent)}"
                Text(
                    text = displayText,
                    style = TextStyle(
                        color = ColorProvider(inkColor),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                if (income > 0) {
                    val spentFraction = (spent / income).coerceIn(0.0, 1.0).toFloat()
                    val isOverBudget = spent > income
                    
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    
                    if (privacyMode) {
                        Text(
                            text = "Balance hidden",
                            style = TextStyle(color = ColorProvider(mutedColor), fontSize = 11.sp)
                        )
                    } else {
                        Text(
                            text = "${(spentFraction * 100).toInt()}% of budget",
                            style = TextStyle(
                                color = ColorProvider(if (isOverBudget) coralColor else jadeColor),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = "₹${fmtINR((income - spent).coerceAtLeast(0.0))} left",
                            style = TextStyle(color = ColorProvider(mutedColor), fontSize = 11.sp)
                        )
                    }
                }
            }
        }
    }

    private fun fmtINR(n: Double): String {
        return NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
            maximumFractionDigits = 0
        }.format(n.toLong())
    }
}

class ExpenseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExpenseWidget()
}
