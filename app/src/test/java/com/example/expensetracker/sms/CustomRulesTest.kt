package com.example.expensetracker.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomRulesTest {
    private fun range(text: String, part: String): IntRange {
        val i = text.indexOf(part); require(i >= 0) { "$part not in text" }
        return i until i + part.length
    }

    private fun payment(example: String, amount: String, payee: String?): ParsingRule {
        val r = CustomRules.build(example, ParsingRule.Kind.PAYMENT, range(example, amount), payee?.let { range(example, it) }, "id", 0)
        return (r as? CustomRules.BuildResult.Ok)?.rule ?: throw AssertionError("build failed: $r")
    }

    // A co-op bank format the built-in parser doesn't know
    private val coop = "Saraswat Bank: A/c XX4521 is debited with INR 1,250.00 on 03Oct24 for POS txn at CROMA RETAIL. Avl Bal INR 20,511.75"

    @Test fun `a taught rule reads the next message of the same format`() {
        val rule = payment(coop, "1,250.00", "CROMA RETAIL")
        val next = "Saraswat Bank: A/c XX4521 is debited with INR 89.00 on 14Nov24 for POS txn at DMART. Avl Bal INR 3,002.10"
        assertEquals(RuleMatch.Payment(89.0, "DMART"), CustomRules.match(listOf(rule), next))
    }

    @Test fun `the example is read back exactly`() {
        assertEquals(RuleMatch.Payment(1250.0, "CROMA RETAIL"), CustomRules.match(listOf(payment(coop, "1,250.00", "CROMA RETAIL")), coop))
    }

    @Test fun `a different format doesn't match`() {
        val rule = payment(coop, "1,250.00", "CROMA RETAIL")
        assertNull(CustomRules.match(listOf(rule), "Saraswat Bank: A/c XX4521 is credited with INR 500.00 on 03Oct24 by NEFT."))
        assertNull(CustomRules.match(listOf(rule), "Your OTP is 123456"))
    }

    @Test fun `payee is optional for messages without one, like ATM withdrawals`() {
        val atm = "Rs 2,000 withdrawn at ATM from A/c XX4521 on 03Oct24. Avl Bal Rs 18,511.75"
        val rule = payment(atm, "2,000", null)
        assertEquals(RuleMatch.Payment(500.0, null), CustomRules.match(listOf(rule), "Rs 500 withdrawn at ATM from A/c XX4521 on 21Nov24. Avl Bal Rs 9,011.00"))
    }

    @Test fun `without a marked payee, the payee's words must match`() {
        // The screen asks the user to mark the payee for exactly this reason.
        val rule = payment(coop, "1,250.00", null)
        assertNull(CustomRules.match(listOf(rule), "Saraswat Bank: A/c XX4521 is debited with INR 75.00 on 14Nov24 for POS txn at DMART. Avl Bal INR 1.00"))
    }

    @Test fun `payee before the amount and at the end of the message`() {
        val ex = "Paid to Ravi Kumar Rs 300 via UPI. Paid to KIRANA STORE"
        val rule = payment(ex, "300", "KIRANA STORE")
        assertEquals(RuleMatch.Payment(45.0, "MILK BOOTH"), CustomRules.match(listOf(rule), "Paid to Ravi Kumar Rs 45 via UPI. Paid to MILK BOOTH"))
        val before = payment("Txn to ZEPTO of INR 199 done", "199", "ZEPTO")
        assertEquals(RuleMatch.Payment(410.0, "BIG BASKET"), CustomRules.match(listOf(before), "Txn to BIG BASKET of INR 410 done"))
    }

    @Test fun `multi-line messages and extra spaces`() {
        val ex = "Sent Rs.250.00\nFrom Bank A/C *1234\nTo ZOMATO LTD\nOn 12/10/24"
        val rule = payment(ex, "250.00", "ZOMATO LTD")
        assertEquals(RuleMatch.Payment(99.5, "UBER"), CustomRules.match(listOf(rule), "Sent Rs.99.50\nFrom Bank A/C *1234\nTo UBER\nOn 01/11/24"))
        assertEquals(RuleMatch.Payment(99.5, "UBER"), CustomRules.match(listOf(rule), "Sent  Rs.99.50\r\nFrom Bank A/C *1234\r\nTo UBER\r\nOn 01/11/24 "))
    }

    @Test fun `ignore rules silence a format`() {
        val promo = "Get 10% cashback up to Rs 500 on your HDFC card. T&C apply. Valid till 31Oct"
        val r = CustomRules.build(promo, ParsingRule.Kind.IGNORE, id = "x", now = 0) as CustomRules.BuildResult.Ok
        assertEquals(RuleMatch.Ignore, CustomRules.match(listOf(r.rule), "Get 5% cashback up to Rs 250 on your HDFC card. T&C apply. Valid till 15Nov"))
        assertNull(CustomRules.match(listOf(r.rule), "Rs 500 debited from your HDFC card"))
    }

    @Test fun `regex characters in the message are taken literally`() {
        val ex = "[Alert] (UPI) Rs.40 paid+ok? to A*B|C ^\$"
        val rule = payment(ex, "40", "A*B|C")
        assertEquals(RuleMatch.Payment(60.0, "X*Y|Z"), CustomRules.match(listOf(rule), "[Alert] (UPI) Rs.60 paid+ok? to X*Y|Z ^\$"))
        assertNull(CustomRules.match(listOf(rule), "XAlertX (UPI) Rs.60 paid+ok? to X*Y|Z ^\$"))
    }

    @Test fun `bad marks are refused`() {
        fun fail(r: CustomRules.BuildResult) = assertTrue("$r", r is CustomRules.BuildResult.Failed)
        fail(CustomRules.build(coop, ParsingRule.Kind.PAYMENT, null, null, "i", 0))
        fail(CustomRules.build(coop, ParsingRule.Kind.PAYMENT, range(coop, "Saraswat"), null, "i", 0))
        fail(CustomRules.build(coop, ParsingRule.Kind.PAYMENT, range(coop, "1,250.00"), range(coop, "250.00 on"), "i", 0))
    }

    @Test fun `tap helpers find the number in a word and trim the payee`() {
        val t = "Debited Rs.500.00 at SWIGGY."
        assertEquals("500.00", t.substring(CustomRules.amountRangeIn(t, range(t, "Rs.500.00"))!!))
        assertNull(CustomRules.amountRangeIn(t, range(t, "Debited")))
        assertEquals("SWIGGY", t.substring(CustomRules.trimPayee(t, range(t, "SWIGGY."))))
    }

    @Test fun `label hides digits and names the marked parts`() {
        val rule = payment(coop, "1,250.00", "CROMA RETAIL")
        assertTrue(rule.label, rule.label.startsWith("Saraswat Bank: A/c XX# is debited with INR <amount> on #Oct# for POS txn at <payee>"))
        assertTrue(!rule.label.contains("4521"))
    }

    @Test fun `first matching rule wins and a broken pattern is skipped`() {
        val broken = ParsingRule("b", ParsingRule.Kind.PAYMENT, "(", 1, 0, "x", 0)
        val good = payment(coop, "1,250.00", "CROMA RETAIL")
        assertEquals(RuleMatch.Payment(1250.0, "CROMA RETAIL"), CustomRules.match(listOf(broken, good), coop))
    }

    @Test fun `the payee's bank code after it may change`() {
        val ex = "Dear Customer, INR 1,250.00 debited from A/c XX4521 on 03-OCT-24. Info: UPI/DR/428712345678/CROMA RETAIL/YESB. Avl bal INR 20,511.75 -Federal Bank"
        val rule = payment(ex, "1,250.00", "CROMA RETAIL")
        val next = "Dear Customer, INR 349.00 debited from A/c XX4521 on 16-OCT-24. Info: UPI/DR/428799990000/BIG BAZAAR/HDFC. Avl bal INR 20,162.75 -Federal Bank"
        assertEquals(RuleMatch.Payment(349.0, "BIG BAZAAR"), CustomRules.match(listOf(rule), next))
    }

    @Test fun `payee followed by a full stop keeps whole multi-word names`() {
        val rule = payment("Rs 120 spent at CROMA RETAIL. Avl Bal Rs 5", "120", "CROMA RETAIL")
        assertEquals(RuleMatch.Payment(80.0, "BIG BAZAAR HYPER"), CustomRules.match(listOf(rule), "Rs 80 spent at BIG BAZAAR HYPER. Avl Bal Rs 9"))
    }

    @Test fun `balances may grow into lakh format`() {
        val ex = "Dear Customer, INR 1,250.00 debited from A/c XX4521 on 03-OCT-24. Info: UPI/DR/428712345678/CROMA RETAIL/YESB. Avl bal INR 20,511.75 -Federal Bank"
        val rule = payment(ex, "1,250.00", "CROMA RETAIL")
        val next = "Dear Customer, INR 12,40,000.00 debited from A/c XX4521 on 16-OCT-24. Info: UPI/DR/428799990000/HOUSE DEPOSIT/HDFC. Avl bal INR 1,18,062.25 -Federal Bank"
        assertEquals(RuleMatch.Payment(1240000.0, "HOUSE DEPOSIT"), CustomRules.match(listOf(rule), next))
        val small = "Dear Customer, INR 5.00 debited from A/c XX4521 on 16-OCT-24. Info: UPI/DR/1/TEA/HDFC. Avl bal INR 7 -Federal Bank"
        assertEquals(RuleMatch.Payment(5.0, "TEA"), CustomRules.match(listOf(rule), small))
    }
}
