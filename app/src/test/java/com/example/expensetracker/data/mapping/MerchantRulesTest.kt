package com.example.expensetracker.data.mapping

import com.example.expensetracker.data.db.entity.MerchantMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantRulesTest {

    @Test fun `learnable names`() {
        assertTrue(MerchantRules.isLearnable("swiggy@icici"))
        assertTrue(MerchantRules.isLearnable("GPay → Swiggy"))
        assertTrue(MerchantRules.isLearnable("Coffee"))
        assertFalse(MerchantRules.isLearnable("mer"))
        assertFalse(MerchantRules.isLearnable("HDFC Bank debit"))
        assertFalse(MerchantRules.isLearnable("GPay"))
        // Names the old regex produced from real bank formats
        assertFalse(MerchantRules.isLearnable("Cust"))
        assertFalse(MerchantRules.isLearnable("412345678901."))
        assertFalse(MerchantRules.isLearnable("GPay: Payment successful ₹250 paid"))
        assertFalse(MerchantRules.isLearnable("Rs.2000.00 withdrawn from a/c **1234 on 12-10-24. Avl bal"))
    }

    @Test fun `matching needs whole words`() {
        val mappings = listOf(MerchantMapping("mer", 1), MerchantMapping("Swiggy", 2), MerchantMapping("Swiggy Instamart", 3))
        // "mer" is too short to match anything except itself
        assertNull(MerchantRules.findCategoryId("AMERICAN TOURISTER", mappings.take(1)))
        assertEquals(2L, MerchantRules.findCategoryId("SWIGGY", mappings))
        assertEquals(2L, MerchantRules.findCategoryId("GPay → Swiggy", mappings))
        // The longer, more specific rule wins
        assertEquals(3L, MerchantRules.findCategoryId("Swiggy Instamart Order", mappings))
        // Not a whole word
        assertNull(MerchantRules.findCategoryId("Swiggyfoods", listOf(MerchantMapping("Swiggy", 2))))
        // Exact match ignores case and still works for short names
        assertEquals(1L, MerchantRules.findCategoryId("MER", mappings))
    }

    @Test fun `broken rules are found, good ones kept`() {
        val mappings = listOf(
            MerchantMapping("mer", 1),
            MerchantMapping("omato", 1),
            MerchantMapping("swiggy@icici", 2),
            MerchantMapping("Rent", 3),
            MerchantMapping("GPay: ₹250 paid to Swiggy", 2),
            MerchantMapping("GPay → mer", 2)
        )
        val bodies = mapOf(
            "omato" to listOf("Rs.250 debited at Zomato on 12-10-24"),
            "swiggy@icici" to listOf("Rs.500 debited to VPA swiggy@icici"),
            "GPay → mer" to listOf("Paid to customer care")
        )
        assertEquals(
            listOf("mer", "omato", "GPay: ₹250 paid to Swiggy", "GPay → mer"),
            MerchantRules.findBroken(mappings, bodies)
        )
    }
}
