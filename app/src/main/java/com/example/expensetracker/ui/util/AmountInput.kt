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
import java.text.NumberFormat
import java.util.Locale

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
    fun validated(): Double? = AmountCalculator.evaluate(value.text).also { if (it == null) error = "Enter a valid amount" }
}

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
    val result = if (AmountCalculator.needsPreview(text)) AmountCalculator.evaluate(text) else null
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
                result != null -> {{ Text("= ₹${previewFormat(result)}", color = Muted, fontSize = 12.sp) }}
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

/** "1,65,000" or "33.33": Indian grouping, paise only when there are some. */
private fun previewFormat(n: Double): String = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
    maximumFractionDigits = 2; minimumFractionDigits = 0
}.format(n)
