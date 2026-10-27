package com.example.expensetracker.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/*
 * The app's dialogs. Each is a window of its own, and a window added after the lock window sits on
 * top of it: a restore preview arriving after the file picker covered the lock screen, and its
 * Restore button worked without unlocking. So a dialog asked for while the app is locked isn't
 * created until it's unlocked. One that was already open stays open under the lock (an older
 * window), with whatever was typed in it. Screens use these, not the Material ones (checked by
 * AppDialogsUsageTest).
 */

/** True while the App Lock screen is up. Provided by MainActivity around the app's screens. */
val LocalAppLocked = compositionLocalOf { false }

/** Shows [dialog] unless it would first appear while the app is locked; then it waits for unlock. */
@Composable
private fun NotOverTheLock(dialog: @Composable () -> Unit) {
    // Not state: setting it needn't recompose anything.
    val opened = remember { BooleanArray(1) }
    if (LocalAppLocked.current && !opened[0]) return
    SideEffect { opened[0] = true }
    dialog()
}

// Same parameters and defaults as the Material versions (material3 1.4).

@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) = NotOverTheLock {
    AlertDialog(
        onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier,
        dismissButton = dismissButton, icon = icon, title = title, text = text, shape = shape,
        containerColor = containerColor, iconContentColor = iconContentColor,
        titleContentColor = titleContentColor, textContentColor = textContentColor,
        tonalElevation = tonalElevation, properties = properties
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    shape: Shape = DatePickerDefaults.shape,
    tonalElevation: Dp = DatePickerDefaults.TonalElevation,
    colors: DatePickerColors = DatePickerDefaults.colors(),
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable ColumnScope.() -> Unit
) = NotOverTheLock {
    DatePickerDialog(
        onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier,
        dismissButton = dismissButton, shape = shape, tonalElevation = tonalElevation, colors = colors,
        properties = properties, content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetGesturesEnabled: Boolean = true,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit
) = NotOverTheLock {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest, modifier = modifier, sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth, sheetGesturesEnabled = sheetGesturesEnabled, shape = shape,
        containerColor = containerColor, contentColor = contentColor, tonalElevation = tonalElevation,
        scrimColor = scrimColor, dragHandle = dragHandle, contentWindowInsets = contentWindowInsets,
        properties = properties, content = content
    )
}
