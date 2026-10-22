package com.example.expensetracker.ui.util

import java.math.BigDecimal

// Shared by every monthly-limit field (Categories dialog, Settings → Category Budgets) so they
// pre-fill and parse identically.

/** Pre-fill text for an existing limit. Keeps decimals so saving without edits doesn't change the value. */
fun formatLimitInput(limit: Double): String =
    if (limit > 0) BigDecimal.valueOf(limit).stripTrailingZeros().toPlainString() else ""

private val ZERO = Regex("""^₹?\s*0+([.,]0*)?$""")

/**
 * Blank or 0 → 0.0 (no limit). Anything else is read exactly like the amount field (₹, grouping,
 * the phone's decimal comma, even sums), so "1,5" is the same number everywhere. Null if unreadable.
 */
fun parseLimitInput(text: String, decimalComma: Boolean = AmountCalculator.usesDecimalComma()): Double? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || ZERO.matches(trimmed)) return 0.0
    return AmountCalculator.evaluate(trimmed, decimalComma)
}
