package com.example.expensetracker.ui.util

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Rotation and Android closing the app: dialogs stay open on the same item, with what was typed. */
@RunWith(AndroidJUnit4::class)
class SaveableStateTest {
    @get:Rule val compose = createComposeRule()

    private data class Item(val id: Long, val name: String)

    @Test fun anAmountKeepsItsTextAfterRecreation() {
        val restore = StateRestorationTester(compose)
        lateinit var state: AmountInputState
        restore.setContent {
            state = rememberAmountInputState("120")
            BasicTextField(state.value, { state.value = it }, Modifier.testTag("amount"))
        }
        compose.onNodeWithTag("amount").performTextInput("+45")
        state.error = "Enter a valid amount"
        restore.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals("120+45", state.value.text)
        assertEquals("Enter a valid amount", state.error)
    }

    @Test fun theItemBeingEditedIsStillOpenAfterRecreation() {
        val restore = StateRestorationTester(compose)
        val items = listOf(Item(1, "Rent"), Item(2, "Netflix"))
        var opened = false
        restore.setContent {
            var editing by rememberSaveableItem(items) { it.id }
            // Opened once, as a tap would; after the restore only the saved id can bring it back.
            if (!opened) { editing = items[1]; opened = true }
            Text(editing?.name ?: "closed")
        }
        compose.onNodeWithText("Netflix").assertExists()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Netflix").assertExists()
    }

    @Test fun anItemDeletedElsewhereClosesItsDialog() {
        var items by mutableStateOf(listOf(Item(1, "Rent"), Item(2, "Netflix")))
        var opened = false
        compose.setContent {
            var editing by rememberSaveableItem(items) { it.id }
            if (!opened) { editing = items[1]; opened = true }
            Text(editing?.name ?: "closed")
        }
        compose.onNodeWithText("Netflix").assertExists()
        items = listOf(Item(1, "Rent"))
        compose.onNodeWithText("closed").assertExists()
    }
}
