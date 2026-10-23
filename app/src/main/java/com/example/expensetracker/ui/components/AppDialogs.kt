package com.example.expensetracker.ui.components

import android.view.View
import android.view.ViewParent
import android.view.Window
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/*
 * The app's dialogs. Each is a window of its own, so one that opens while the app is locked (a
 * restore preview arriving after the file picker, say) would sit on top of the lock window. These
 * hide their window while [LocalAppLocked] is true: it stays open, keeps what was typed, and is back
 * on unlock. Screens use these, not the Material ones (checked by AppDialogsUsageTest).
 */

/** True while the App Lock screen is up. Provided by MainActivity around the app's screens. */
val LocalAppLocked = compositionLocalOf { false }

/** Hides the dialog window this is composed in while locked: no drawing, focus, dimming or TalkBack. */
@Composable
private fun HideWhileLocked() {
    val locked = LocalAppLocked.current
    val view = LocalView.current
    DisposableEffect(locked, view) {
        view.dialogWindow()?.decorView?.visibility = if (locked) View.INVISIBLE else View.VISIBLE
        onDispose {}
    }
}

/** The window is only hidden once the content has composed; this keeps the first frame blank too. */
@Composable
private fun lockedAlpha(): Modifier = if (LocalAppLocked.current) Modifier.alpha(0f) else Modifier

private fun View.dialogWindow(): Window? {
    var parent: ViewParent? = parent
    while (parent != null) {
        if (parent is DialogWindowProvider) return parent.window
        parent = parent.parent
    }
    return null
}

@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    containerColor: Color = AlertDialogDefaults.containerColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { HideWhileLocked(); confirmButton() },
        modifier = lockedAlpha(),
        dismissButton = dismissButton,
        title = title, text = text,
        containerColor = containerColor, titleContentColor = titleContentColor, textContentColor = textContentColor
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { HideWhileLocked(); confirmButton() },
        modifier = lockedAlpha(),
        dismissButton = dismissButton, content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest, modifier = lockedAlpha(), sheetState = sheetState,
        containerColor = containerColor, dragHandle = dragHandle
    ) { HideWhileLocked(); content() }
}
