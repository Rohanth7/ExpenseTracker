package com.example.expensetracker.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sample messages follow real Indian bank formats; account numbers, references and names are
 * made up. Add a sample here for every parser bug that gets fixed.
 */
class SmsParserTest {

    private fun parse(sender: String, body: String): ParsedTransaction? {
        if (!SmsParser.isTransactionSms(sender, body)) return null
        return SmsParser.parse(sender, body)
    }

    private fun assertDebit(sender: String, body: String, amount: Double, description: String) {
        val tx = parse(sender, body)
        assertNotNull("Expected a debit for: $body", tx)
        assertEquals(amount, tx!!.amount, 0.001)
        assertEquals(description, tx.description)
    }

    private fun assertIgnored(sender: String, body: String) {
        assertNull("Expected no expense for: $body", parse(sender, body))
    }

    // ── Merchant names ───────────────────────────────────────────

    @Test fun `Customer is not turned into mer`() = assertDebit(
        "AD-HDFCBK",
        "Dear Customer, Rs.500.00 has been debited from account **1234 to VPA swiggy@icici on 12-10-24. Your UPI transaction reference number is 412345678901.",
        500.0, "swiggy@icici"
    )

    @Test fun `HDFC UPI multi-line`() = assertDebit(
        "VM-HDFCBK",
        "Sent Rs.250.00\nFrom HDFC Bank A/C *1234\nTo ZOMATO LTD\nOn 12/10/24\nRef 412345678901\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808",
        250.0, "ZOMATO LTD"
    )

    @Test fun `HDFC VPA with footer that says call to report`() = assertDebit(
        "AD-HDFCBK",
        "Rs.1,499.00 debited from a/c **1234 on 12-10-24 to VPA netflix.upi@hdfcbank(UPI Ref No 412345678901). Not you? Call on 18002586161 to report",
        1499.0, "netflix.upi@hdfcbank"
    )

    @Test fun `HDFC card spend`() = assertDebit(
        "AD-HDFCBK",
        "Spent Rs.2,345.50 On HDFC Bank Card 1234 At AMAZON PAY INDIA On 2024-10-12:10:11:12 Bal Rs.10,000.00 Not You? Call 18002586161",
        2345.50, "AMAZON PAY INDIA"
    )

    @Test fun `ICICI UPI uses the name before credited`() = assertDebit(
        "JD-ICICIB",
        "ICICI Bank Acct XX123 debited for Rs 349.00 on 12-Oct-24; BIGBASKET credited. UPI:412345678901. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766.",
        349.0, "BIGBASKET"
    )

    @Test fun `ICICI card spend`() = assertDebit(
        "JD-ICICIT",
        "INR 1,234.00 spent using ICICI Bank Card XX1234 on 12-Oct-24 on MYNTRA. Avl Limit: INR 50,000.00. If not you, call 1800 2662/SMS BLOCK 1234 to 9215676766.",
        1234.0, "MYNTRA"
    )

    @Test fun `SBI UPI amount without Rs`() = assertDebit(
        "BZ-SBIUPI",
        "Dear UPI user A/C X1234 debited by 120.0 on date 12Oct24 trf to UBER INDIA Refno 412345678901. If not u? call 1800111109. -SBI",
        120.0, "UBER INDIA"
    )

    @Test fun `SBI credit card`() = assertDebit(
        "AD-SBICRD",
        "Rs.799.00 spent on your SBI Credit Card ending 1234 at DECATHLON on 12/10/24. Trxn not done by you? Report at https://sbicard.com/Dispute",
        799.0, "DECATHLON"
    )

    @Test fun `Axis UPI P2M`() = assertDebit(
        "AX-AXISBK",
        "INR 89.00 debited\nA/c no. XX1234\n12-10-24, 10:11:12\nUPI/P2M/412345678901/BLINKIT\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank",
        89.0, "BLINKIT"
    )

    @Test fun `Kotak sent to VPA`() = assertDebit(
        "AD-KOTAKB",
        "Sent Rs.60.00 from Kotak Bank AC X1234 to chaiwala@ybl on 12-10-24.UPI Ref 412345678901. Not you, https://kotak.com/KBANKT/Fraud",
        60.0, "chaiwala@ybl"
    )

    @Test fun `no payee found falls back to the bank name, not the SMS text`() = assertDebit(
        "AD-HDFCBK",
        "Rs.2000.00 withdrawn from a/c **1234 on 12-10-24. Avl bal Rs.5,000.00",
        2000.0, "HDFC Bank debit"
    )

    // ── Messages that are not expenses ───────────────────────────

    @Test fun `OTP for a card transaction`() = assertIgnored(
        "AD-HDFCBK",
        "OTP is 123456 for txn of INR 2,999.00 at AMAZON on HDFC Bank card ending 1234. Valid till 10:15. Do not share OTP for security reasons."
    )

    @Test fun `credit card bill due reminder`() = assertIgnored(
        "AD-ICICIB",
        "ICICI Bank Credit Card XX1234 statement: Total Amt Due Rs 12,345.00, Min Amt Due Rs 620.00, payment due by 20-Oct-24. Pay now to avoid charges."
    )

    @Test fun `will be debited notice`() = assertIgnored(
        "AD-HDFCBK",
        "Rs.199.00 will be debited from your a/c **1234 on 15-10-24 towards NETFLIX as per your e-mandate."
    )

    @Test fun `mandate registration`() = assertIgnored(
        "AD-HDFCBK",
        "Your UPI AutoPay mandate for Rs.649.00 to SPOTIFY has been registered successfully on a/c **1234."
    )

    @Test fun `autopay that actually ran is still logged`() = assertDebit(
        "AD-HDFCBK",
        "Rs.199.00 debited from a/c **1234 on 15-10-24 towards e-mandate NETFLIX. Ref 412345678901",
        199.0, "e-mandate NETFLIX"
    )

    @Test fun `collect request`() = assertIgnored(
        "AD-SBIUPI",
        "Rahul has requested money Rs.500.00 from you via UPI. Approve in your UPI app to pay."
    )

    @Test fun `failed payment`() = assertIgnored(
        "AD-HDFCBK",
        "Your payment of Rs.1,200.00 to FLIPKART from a/c **1234 has failed. Any amount debited will be refunded."
    )

    @Test fun `credit to account`() = assertIgnored(
        "AD-HDFCBK",
        "Rs.25,000.00 credited to your a/c **1234 on 01-10-24 by NEFT from ACME PVT LTD."
    )

    @Test fun `prepaid recharge promo`() = assertIgnored(
        "VK-AIRTEL",
        "Recharge with Rs.299 prepaid pack and get unlimited calls. Click here to avail the offer."
    )

    // ── Helpers ──────────────────────────────────────────────────

    @Test fun `credit card detection`() {
        assertTrue(SmsParser.isCreditCardSms("Rs.799.00 spent on your SBI Credit Card ending 1234"))
        assertEquals("UPI", SmsParser.detectPaymentMethod("Rs.50 debited from a/c **1234"))
    }
}
