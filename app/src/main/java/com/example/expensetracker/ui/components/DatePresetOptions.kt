package com.example.expensetracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.export.ExportPreset
import com.example.expensetracker.data.period.BudgetPeriod
import com.example.expensetracker.ui.theme.Ink
import com.example.expensetracker.ui.theme.Jade
import com.example.expensetracker.ui.theme.Muted

/**
 * One radio row per [ExportPreset]; "Choose dates" opens the date-range picker and then shows the
 * dates picked. Closing the picker without dates goes back to the option chosen before, since
 * "Choose dates" needs some. [allLabel] names [ExportPreset.ALL].
 */
@Composable
fun DatePresetOptions(
    preset: ExportPreset,
    custom: Pair<Long, Long>?,
    pickerTitle: String,
    allLabel: String = ExportPreset.ALL.label,
    onChange: (ExportPreset, Pair<Long, Long>?) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    var beforePicker by remember { mutableStateOf(preset) }
    Column {
        ExportPreset.entries.forEach { p ->
            val select = {
                if (p == ExportPreset.CUSTOM) { if (preset != ExportPreset.CUSTOM) beforePicker = preset; showPicker = true }
                onChange(p, custom)
            }
            Row(Modifier.fillMaxWidth().clickable(onClick = select), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = preset == p, onClick = select,
                    colors = RadioButtonDefaults.colors(selectedColor = Jade, unselectedColor = Muted))
                Text(when {
                    p == ExportPreset.ALL -> allLabel
                    p == ExportPreset.CUSTOM && custom != null -> BudgetPeriod.dayRangeLabel(custom.first, custom.second)
                    else -> p.label
                }, color = Ink, fontSize = 14.sp)
            }
        }
    }
    if (showPicker) DateRangePickerDialog(pickerTitle, initial = custom,
        onDismiss = { showPicker = false; if (custom == null) onChange(beforePicker, null) }) {
        showPicker = false
        onChange(ExportPreset.CUSTOM, it)
    }
}
