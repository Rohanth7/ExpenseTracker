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
    @Test fun `knows when text is an expression`() {
        assertTrue(AmountCalculator.isExpression("120+45")); assertTrue(AmountCalculator.isExpression("3×40"))
        assertFalse(AmountCalculator.isExpression("250")); assertFalse(AmountCalculator.isExpression("1,200"))
    }
}
