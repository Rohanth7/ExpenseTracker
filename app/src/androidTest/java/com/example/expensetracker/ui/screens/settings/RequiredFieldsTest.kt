package com.example.expensetracker.ui.screens.settings

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
import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.ui.screens.dashboard.AddGoalDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Goal, Bill and Loan dialogs: the confirm button is enabled only when every required field
 * is valid, and a wrong value says why. Before, it was enabled and did nothing (BUG-27).
 */
@RunWith(AndroidJUnit4::class)
class RequiredFieldsTest {
    @get:Rule val compose = createComposeRule()

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))
    private fun type(label: String, text: String) = field(label).performTextInput(text)
    private fun retype(label: String, text: String) = field(label).run { performTextClearance(); performTextInput(text) }

    @Test fun goalNeedsANameAndATarget() {
        var created: Pair<String, Double>? = null
        compose.setContent { AddGoalDialog(onDismiss = {}, onConfirm = { name, target -> created = name to target }) }
        type("Target Amount (₹)", "5000")
        compose.onNodeWithText("Create").assertIsNotEnabled()
        type("What are you saving for?", "Goa trip")
        compose.onNodeWithText("Create").assertIsEnabled().performClick()
        assertEquals("Goa trip" to 5000.0, created)
    }

    @Test fun billNeedsANameAmountAndADayOfTheMonth() {
        var added: List<Any>? = null
        compose.setContent { AddBillDialog(emptyList(), onDismiss = {}, onConfirm = { n, a, d, r, _, auto -> added = listOf(n, a, d, r, auto) }) }
        type("Amount (₹)", "15000")
        type("Due day", "45")
        type("Name (e.g. Rent, Netflix)", "Rent")
        compose.onNodeWithText("Add").assertIsNotEnabled()
        compose.onNodeWithText("Enter 1 to 31").assertExists()
        retype("Due day", "5")
        retype("Remind N days before", "20")
        compose.onNodeWithText("Add").assertIsNotEnabled()
        compose.onNodeWithText("Enter 0 to 14 days").assertExists()
        retype("Remind N days before", "2")
        compose.onNodeWithText("Add").assertIsEnabled().performClick()
        assertEquals(listOf("Rent", 15000.0, 5, 2, false), added)
    }

    @Test fun editingABillCantBlankItsName() {
        var saved: Bill? = null
        compose.setContent { EditBillDialog(Bill(name = "Rent", amount = 15000.0, dueDayOfMonth = 5), emptyList(), onDismiss = {}, onConfirm = { saved = it }) }
        field("Name").performTextClearance()
        compose.onNodeWithText("Save").assertIsNotEnabled().performClick()
        assertNull(saved)
    }

    @Test fun loanNeedsANameEmiAndATenureOfAtLeastAMonth() {
        var added: List<Any>? = null
        compose.setContent { AddLoanDialog(onDismiss = {}, onConfirm = { n, e, _, emi, t, _, d -> added = listOf(n, e, emi, t, d) }) }
        type("Total (₹)", "60000")
        type("EMI (₹)", "5000")
        type("Tenure (mo)", "0")
        compose.onNodeWithText("Add").assertIsNotEnabled()
        type("Loan name", "Car")
        compose.onNodeWithText("Add").assertIsNotEnabled()
        compose.onNodeWithText("1 to 1200 months").assertExists()
        retype("Tenure (mo)", "12")
        retype("Due day of month", "32")
        compose.onNodeWithText("Add").assertIsNotEnabled()
        retype("Due day of month", "31")
        compose.onNodeWithText("Add").assertIsEnabled().performClick()
        assertEquals(listOf("Car", "🏦", 5000.0, 12, 31), added)
    }

    @Test fun editingALoanCantClearItsTenure() {
        var saved: Loan? = null
        compose.setContent { EditLoanDialog(Loan(name = "Car", totalAmount = 60000.0, monthlyEmi = 5000.0, tenureMonths = 12, startDate = 1L), onDismiss = {}, onConfirm = { saved = it }) }
        field("Tenure (mo)").performTextClearance()
        compose.onNodeWithText("Save").assertIsNotEnabled().performClick()
        assertNull(saved)
    }

    /** BUG-26: the icon took at most 2 UTF-16 units, so 🇮🇳 (4) or 👨‍👩‍👧 (8) was refused. */
    @Test fun theLoanIconTakesAnyOneEmoji() {
        var emoji: String? = null
        compose.setContent { AddLoanDialog(onDismiss = {}, onConfirm = { _, e, _, _, _, _, _ -> emoji = e }) }
        type("Loan name", "Home")
        type("Total (₹)", "0")
        type("EMI (₹)", "35000")
        type("Tenure (mo)", "180")
        // Typed after the 🏦 that's there: the new one replaces it
        type("Icon", "🇮🇳")
        compose.onNodeWithText("Add").performClick()
        assertEquals("🇮🇳", emoji)
        retype("Icon", "👨‍👩‍👧")
        compose.onNodeWithText("Add").performClick()
        assertEquals("👨‍👩‍👧", emoji)
    }
}
