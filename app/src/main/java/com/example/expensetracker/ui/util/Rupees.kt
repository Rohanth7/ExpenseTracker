package com.example.expensetracker.ui.util

import java.text.NumberFormat
import java.util.Locale

/** "₹1,20,000": whole rupees, Indian grouping. Subscriptions, the Overview and price alerts use it. */
internal fun formatRupees(amount: Double): String =
    "₹" + NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply { maximumFractionDigits = 0 }.format(amount)
