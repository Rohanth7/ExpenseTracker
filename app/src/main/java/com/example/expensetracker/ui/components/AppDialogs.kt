package com.example.expensetracker.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var opened by remember { mutableStateOf(false) }
    if (LocalAppLocked.current && !opened) return
    SideEffect { opened = true }
    dialog()
}

@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    containerColor: Color = AlertDialogDefaults.containerColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    properties: DialogProperties = DialogProperties()
) = NotOverTheLock {
    AlertDialog(
        onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier,
        dismissButton = dismissButton, icon = icon, title = title, text = text,
        containerColor = containerColor, titleContentColor = titleContentColor, textContentColor = textContentColor,
        properties = properties
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) = NotOverTheLock {
    DatePickerDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, dismissButton = dismissButton, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit
) = NotOverTheLock {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest, sheetState = sheetState,
        containerColor = containerColor, dragHandle = dragHandle, content = content
    )
}
