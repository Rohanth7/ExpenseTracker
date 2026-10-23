package com.example.expensetracker.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * App dialogs are separate windows, so one that first opens while the app is locked (a restore
 * preview arriving after the file picker) would sit on top of the lock window and take taps. While
 * [LocalAppLocked] is true such a dialog isn't created at all; it appears on unlock. One that was
 * already open stays open (under the lock) and keeps what was typed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class AppDialogsTest {
    @get:Rule val compose = createComposeRule()
    private var locked by mutableStateOf(false)

    private fun show(dialog: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalAppLocked provides locked) { dialog() }
    }

    /** Asked for while locked: no window until unlock, then shown as normal. */
    private fun checkWaitsForUnlock(text: String, dialog: @Composable () -> Unit) {
        locked = true
        show(dialog)
        compose.onNodeWithText(text).assertDoesNotExist()
        locked = false
        compose.waitForIdle()
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun alertDialogAskedForWhileLockedWaitsForUnlock() = checkWaitsForUnlock("Restore this backup?") {
        AppAlertDialog(
            onDismissRequest = {},
            confirmButton = { TextButton(onClick = {}) { Text("Restore") } },
            title = { Text("Restore this backup?") }
        )
    }

    @Test
    fun datePickerAskedForWhileLockedWaitsForUnlock() = checkWaitsForUnlock("Pick") {
        AppDatePickerDialog(
            onDismissRequest = {},
            confirmButton = { TextButton(onClick = {}) { Text("Pick") } }
        ) { DatePicker(state = rememberDatePickerState()) }
    }

    @Test
    fun bottomSheetAskedForWhileLockedWaitsForUnlock() = checkWaitsForUnlock("Notifications") {
        AppModalBottomSheet(onDismissRequest = {}) { Text("Notifications") }
    }

    @Test
    fun dialogOpenBeforeTheLockStaysOpenAndKeepsWhatWasTyped() {
        show {
            var amount by rememberSaveable { mutableStateOf("") }
            AppAlertDialog(
                onDismissRequest = {},
                confirmButton = { TextButton(onClick = {}) { Text("Save") } },
                title = { Text("Add Expense") },
                text = { OutlinedTextField(value = amount, onValueChange = { amount = it }) }
            )
        }
        compose.onNode(hasSetTextAction()).performTextInput("250")
        locked = true
        compose.waitForIdle()
        compose.onNodeWithText("Add Expense").assertExists()
        locked = false
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasText("250")).assertIsDisplayed()
    }
}
