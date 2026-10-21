package com.example.expensetracker.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountCalculatorTest {
    private fun eval(s: String) = AmountCalculator.evaluate(s)

    @Test fun `plain numbers still work`() { assertEquals(250.0, eval("250")!!, 0.0); assertEquals(99.5, eval("99.50")!!, 0.0) }
    @Test fun `adds and subtracts`() { assertEquals(165.0, eval("120+45")!!, 0.0); assertEquals(70.0, eval("100-30")!!, 0.0) }
    @Test fun `multiplication before addition`() = assertEquals(60.0, eval("100-20*2")!!, 0.0)
    @Test fun `accepts × ÷ and the minus sign`() {
        assertEquals(120.0, eval("3×40")!!, 0.0); assertEquals(22.5, eval("90÷4")!!, 0.0); assertEquals(80.0, eval("100−20")!!, 0.0)
    }
    @Test fun `ignores rupee sign, Indian commas and spaces`() {
        assertEquals(1500.0, eval("1,200 + 300")!!, 0.0); assertEquals(250.0, eval("₹ 250")!!, 0.0)
    }
    @Test fun `rounds to paise`() { assertEquals(0.3, eval("0.1+0.2")!!, 0.0); assertEquals(33.33, eval("100/3")!!, 0.0) }
    @Test fun `rejects invalid input`() {
        listOf("", "abc", "120+", "+", "10/0", "50-80", "1..2", "12a").forEach { assertNull("'$it'", eval(it)) }
    }
    @Test fun `commas in Indian and Western grouping are thousands separators`() {
        assertEquals(120000.0, eval("1,20,000")!!, 0.0); assertEquals(1234567.0, eval("12,34,567")!!, 0.0)
        assertEquals(1234567.0, eval("1,234,567")!!, 0.0); assertEquals(1200.5, eval("1,200.50")!!, 0.0)
    }
    @Test fun `a comma used as the decimal point is read as one, not dropped`() {
        // Comma-decimal keyboards: "12,50" used to become 1250.
        assertEquals(12.5, eval("12,50")!!, 0.0); assertEquals(99.9, eval("99,9")!!, 0.0); assertEquals(180.0, eval("180,00")!!, 0.0)
        assertEquals(22.5, eval("10,5+12")!!, 0.0)
    }
    @Test fun `commas that are neither grouping nor a decimal point are refused`() {
        listOf("1,2,3", "1,2345", "12,5.5", "1,,000", ",5", "5,").forEach { assertNull("'$it'", eval(it)) }
    }
    @Test fun `amounts that round to zero paise are refused`() {
        assertNull(eval("0.004")); assertNull(eval("1/1000")); assertEquals(0.01, eval("0.005")!!, 0.0)
    }
    @Test fun `absurdly large amounts are refused instead of overflowing`() {
        assertNull(eval("99999999999999999")); assertNull(eval("1000000000*1000000000"))
        assertEquals(1_00_00_00_000.0, eval("1000000000")!!, 0.0)  // ₹100 crore still fine
    }
    @Test fun `amounts are written for editing without the phone's locale`() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)  // "%.2f" would give "180,00"
            assertEquals("180.00", AmountCalculator.forEditing(180.0)); assertEquals("1234.50", AmountCalculator.forEditing(1234.5))
        } finally { java.util.Locale.setDefault(saved) }
    }
    @Test fun `an operator chip inserts at the cursor and moves the cursor after it`() {
        assertEquals("3×" to 2, AmountCalculator.insertAt("3", 1, 1, "×"))
        assertEquals("12+3" to 3, AmountCalculator.insertAt("123", 2, 2, "+"))
        assertEquals("1÷4" to 2, AmountCalculator.insertAt("1234", 1, 3, "÷"))  // replaces a selection
    }
    @Test fun `a cursor outside the text is clamped`() = assertEquals("5−" to 2, AmountCalculator.insertAt("5", 9, 9, "−"))
    @Test fun `knows when text is an expression`() {
        assertTrue(AmountCalculator.isExpression("120+45")); assertTrue(AmountCalculator.isExpression("3×40"))
        assertFalse(AmountCalculator.isExpression("250")); assertFalse(AmountCalculator.isExpression("1,200"))
    }
}
