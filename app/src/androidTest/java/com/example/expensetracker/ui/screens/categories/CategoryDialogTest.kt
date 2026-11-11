package com.example.expensetracker.ui.screens.categories

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.entity.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** B-05: Save was always enabled, and did nothing for a blank name or a limit it couldn't read. */
@RunWith(AndroidJUnit4::class)
class CategoryDialogTest {
    @get:Rule val compose = createComposeRule()
    private var saved: Pair<String, Double>? = null

    private fun show(initial: Category? = null, siblings: List<String> = listOf("Food", "Rent")) = compose.setContent {
        CategoryDialog(initial = initial, siblingNames = siblings, onDismiss = {}, onConfirm = { name, _, _, limit -> saved = name to limit })
    }
    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))
    private fun retype(label: String, text: String) = field(label).run { performTextClearance(); performTextInput(text) }
    private val save get() = compose.onNodeWithText("Save")

    @Test fun aBlankNameOrAnUnreadableLimitCantBeSaved() {
        show()
        save.assertIsNotEnabled().performClick()
        retype("Name", "   ")
        save.assertIsNotEnabled()
        retype("Name", "Travel")
        save.assertIsEnabled()
        retype("Monthly limit ₹ (optional)", "abc")
        save.assertIsNotEnabled().performClick()
        compose.onNodeWithText("Enter a valid amount").assertExists()
        assertNull(saved)
        retype("Monthly limit ₹ (optional)", "1,500")
        save.assertIsEnabled().performClick()
        assertEquals("Travel" to 1500.0, saved)
    }

    @Test fun aNameAlreadyInUseIsRefusedWhateverItsCase() {
        show()
        retype("Name", "food")
        compose.onNodeWithText("There's already one with this name").assertExists()
        save.assertIsNotEnabled().performClick()
        assertNull(saved)
    }

    @Test fun editingKeepsItsOwnNameAndNoLimitMeansNone() {
        show(initial = Category(id = 1, name = "Food", monthlyLimit = 2000.0), siblings = listOf("Rent"))
        save.assertIsEnabled()
        retype("Monthly limit ₹ (optional)", "")
        save.assertIsEnabled().performClick()
        assertEquals("Food" to 0.0, saved)
    }

    @Test fun aNameStopsAtFortyCharacters() {
        show()
        retype("Name", "a".repeat(40))
        field("Name").performTextInput("b")
        save.performClick()
        assertEquals("a".repeat(40), saved?.first)
    }
}
