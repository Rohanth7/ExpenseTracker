package com.example.expensetracker.ui.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

// Shared by every monthly-limit field (Categories dialog, Settings → Category Budgets) so they
// pre-fill and parse identically.

/**
 * Pre-fill text for an existing limit, to the paisa (as saving stores it) so saving without edits
 * doesn't change it. Never three decimals: "1.125" reads as 1,125 on a comma-decimal phone.
 */
fun formatLimitInput(limit: Double): String =
    if (limit > 0) BigDecimal.valueOf(limit).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() else ""

private val ZERO = Regex("""^₹?\s*0+([.,]0*)?$""")

/**
 * Blank or 0 → 0.0 (no limit). Anything else is read exactly like the amount field (₹, grouping,
 * the phone's decimal comma, even sums), so "1,5" is the same number as an expense or a limit.
 * Null if unreadable.
 */
fun parseLimitInput(text: String, decimalComma: Boolean = AmountCalculator.usesDecimalComma()): Double? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || ZERO.matches(trimmed)) return 0.0
    return AmountCalculator.evaluate(trimmed, decimalComma)
}

/**
 * "= ₹50" under a limit field when its text could be read two ways or is a sum, as the amount field
 * shows: "50,00" is ₹50 (a decimal comma), and a misread limit would otherwise be saved unseen.
 */
fun limitPreview(
    text: String,
    decimalComma: Boolean = AmountCalculator.usesDecimalComma(),
    locale: Locale = Locale.getDefault()
): String? {
    if (!AmountCalculator.needsPreview(text.trim(), decimalComma)) return null
    val limit = parseLimitInput(text, decimalComma)?.takeIf { it > 0 } ?: return null
    return "= ₹" + AmountCalculator.formatPreview(limit, locale)
}
