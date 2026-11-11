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

    /** B-02: a ₹0 bill could be saved, and with auto-log it added a ₹0 expense every month. */
    @Test fun aBillAmountMustBeAboveZero() {
        var added: Double? = null
        compose.setContent { AddBillDialog(emptyList(), onDismiss = {}, onConfirm = { _, a, _, _, _, _ -> added = a }) }
        type("Name (e.g. Rent, Netflix)", "Rent")
        type("Due day", "5")
        type("Amount (₹)", "0")
        compose.onNodeWithText("Add").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Enter a valid amount").assertExists()
        assertNull(added)
        retype("Amount (₹)", "0.5")
        compose.onNodeWithText("Add").assertIsEnabled().performClick()
        assertEquals(0.5, added!!, 0.0)
    }

    /** A bill saved with ₹0 before B-02 opens with the amount flagged and can't be saved until it has one. */
    @Test fun aBillSavedWithZeroNeedsAnAmountToBeSavedAgain() {
        var saved: Bill? = null
        compose.setContent { EditBillDialog(Bill(name = "Rent", amount = 0.0, dueDayOfMonth = 5), emptyList(), onDismiss = {}, onConfirm = { saved = it }) }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        retype("Amount (₹)", "15000")
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(15000.0, saved!!.amount, 0.0)
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

    /** B-07: a blank "1st EMI, months ago" was saved as 0, so a loan 10 months in went back to its first month. */
    @Test fun editingALoanCantBlankWhenItStarted() {
        val tenMonthsAgo = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, -10); set(java.util.Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        var saved: Loan? = null
        compose.setContent { EditLoanDialog(Loan(name = "Car", totalAmount = 60000.0, monthlyEmi = 5000.0, tenureMonths = 12, startDate = tenMonthsAgo), onDismiss = {}, onConfirm = { saved = it }) }
        field("1st EMI, months ago").performTextClearance()
        compose.onNodeWithText("Save").assertIsNotEnabled().performClick()
        compose.onNodeWithText("0 to 1200 months").assertExists()
        assertNull(saved)
        type("1st EMI, months ago", "10")
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(10, monthsBetween(saved!!.startDate))
    }

    @Test fun addingALoanNeedsWhenItStarted() {
        var start: Long? = null
        compose.setContent { AddLoanDialog(onDismiss = {}, onConfirm = { _, _, _, _, _, s, _ -> start = s }) }
        type("Loan name", "Car"); type("Total (₹)", "60000"); type("EMI (₹)", "5000"); type("Tenure (mo)", "12")
        compose.onNodeWithText("Add").assertIsEnabled()
        field("1st EMI, months ago").performTextClearance()
        compose.onNodeWithText("Add").assertIsNotEnabled()
        // Past any loan: it used to overflow and be saved as 0
        type("1st EMI, months ago", "99999999999")
        compose.onNodeWithText("Add").assertIsNotEnabled().performClick()
        assertNull(start)
        retype("1st EMI, months ago", "3")
        compose.onNodeWithText("Add").assertIsEnabled().performClick()
        assertEquals(3, monthsBetween(start!!))
    }

    private fun monthsBetween(start: Long): Int {
        val now = java.util.Calendar.getInstance()
        val then = java.util.Calendar.getInstance().apply { timeInMillis = start }
        return (now.get(java.util.Calendar.YEAR) - then.get(java.util.Calendar.YEAR)) * 12 + now.get(java.util.Calendar.MONTH) - then.get(java.util.Calendar.MONTH)
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
