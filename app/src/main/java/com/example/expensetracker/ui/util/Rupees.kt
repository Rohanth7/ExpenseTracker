package com.example.expensetracker.ui.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** "₹1,20,000": whole rupees, Indian grouping. Subscriptions, the Overview and price alerts use it. */
internal fun formatRupees(amount: Double): String =
    "₹" + NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply { maximumFractionDigits = 0 }.format(amount)

/**
 * An amount as every screen, the widget and notifications show it, without the ₹: "1,20,000" for
 * whole rupees, "99.99" / "0.50" when it has paise (rounded to the paisa, never cut off). Indian
 * grouping, written out here so it reads the same on every phone and on the JVM.
 */
fun fmtINR(amount: Double): String {
    if (amount.isNaN() || amount.isInfinite()) return "—"
    val paise = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
    val whole = paise.abs().toBigInteger().toString()
    val grouped = if (whole.length <= 3) whole
        else whole.dropLast(3).reversed().chunked(2).joinToString(",").reversed() + "," + whole.takeLast(3)
    val fraction = paise.abs().remainder(BigDecimal.ONE)
    val text = if (fraction.signum() == 0) grouped else grouped + "." + fraction.toPlainString().substringAfter('.')
    return if (paise.signum() < 0) "-$text" else text
}
