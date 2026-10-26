package com.example.expensetracker.ui.util

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.ui.theme.Muted

/**
 * One amount field's state: the text with its cursor (so a chip's symbol lands at the cursor) and
 * the error under it. The single place that turns typed text into an amount.
 */
@Stable
class AmountInputState(initial: String) {
    var value by mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    var error by mutableStateOf("")

    /** Replaces the text, cursor at the end (e.g. once the expense being edited has loaded). */
    fun setText(text: String) { value = TextFieldValue(text, TextRange(text.length)); error = "" }

    /** The amount, or null after showing "Enter a valid amount" under the field. */
    fun validated(): Double? = AmountCalculator.evaluate(value.text).also { if (it == null) error = INVALID_AMOUNT }
}

const val INVALID_AMOUNT = "Enter a valid amount"

/**
 * A plain amount field's text (bills, loans, goals, income, limits, filters) read as an amount.
 * [value] is null when it isn't one. [invalid] is only for text that's finished but wrong: nothing
 * typed yet, or "1," / "120+" on the way to a number, isn't an error. [preview] is the "= ₹…"
 * line, shown when the text is a sum or could be read two ways.
 */
@Immutable
class AmountText(val value: Double?, val invalid: Boolean, val preview: String?) {
    /** For OutlinedTextField's supportingText: the error in the error colour, else the preview, muted. */
    val supportingText: (@Composable () -> Unit)?
        get() = when {
            invalid -> {{ Text(INVALID_AMOUNT) }}
            preview != null -> {{ Text(preview, color = Muted, fontSize = 12.sp) }}
            else -> null
        }
}

/** Reads [text] with [parse]: [AmountCalculator.evaluate] for amounts, [parseLimitInput] where blank or 0 means none. */
fun readAmountText(
    text: String, parse: (String) -> Double?,
    decimalComma: Boolean = AmountCalculator.usesDecimalComma(), locale: java.util.Locale = java.util.Locale.getDefault()
): AmountText {
    val value = parse(text)
    val trimmed = text.trim()
    val unfinished = trimmed.isEmpty() || trimmed.last() in UNFINISHED_ENDINGS
    return AmountText(value, invalid = value == null && !unfinished, preview = AmountCalculator.previewText(text, value, decimalComma, locale))
}

private const val UNFINISHED_ENDINGS = ",.+-−×÷*/xX"

/** [readAmountText], read again only when the text changes. */
@Composable
fun rememberAmountText(text: String, parse: (String) -> Double?): AmountText = remember(text) { readAmountText(text, parse) }

@Composable
fun rememberAmountInputState(initial: String = ""): AmountInputState = remember { AmountInputState(initial) }

/**
 * An amount field that takes sums like "120+45" or "3×40" (see [AmountCalculator]). The number
 * keyboard has no × or ÷, so chips insert the operators. "= ₹165" shows under it for a sum, or
 * whenever a comma was typed, since a comma can mean thousands or decimals.
 */
@Composable
fun AmountField(state: AmountInputState, modifier: Modifier = Modifier, label: String = "Amount (₹)") {
    val text = state.value.text
    // Parsed once per text change, not on every recomposition.
    val preview = remember(text) { AmountCalculator.previewText(text, AmountCalculator.evaluate(text)) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        OutlinedTextField(
            value = state.value,
            onValueChange = { state.value = it; state.error = "" },
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = state.error.isNotEmpty(),
            supportingText = when {
                state.error.isNotEmpty() -> {{ Text(state.error) }}
                preview != null -> {{ Text(preview, color = Muted, fontSize = 12.sp) }}
                else -> null
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("+", "−", "×", "÷").forEach { op ->
                AssistChip(
                    onClick = {
                        val v = state.value
                        val (newText, cursor) = AmountCalculator.insertAt(v.text, v.selection.start, v.selection.end, op)
                        state.value = TextFieldValue(newText, TextRange(cursor))
                        state.error = ""
                    },
                    label = { Text(op, fontSize = 14.sp) }
                )
            }
        }
    }
}
