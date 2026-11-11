package com.example.expensetracker.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // BUG-19: the dot after "Mr" ended the name, and "Mr" would have been learned as a payee.
    @Test fun `title before the payee's name is skipped`() = assertDebit(
        "VM-KOTAKB",
        "Rs 640.00 sent to Mr. Rahul Sharma from A/c XX12 on 05-11-26. UPI Ref 530912345601",
        640.0, "Rahul Sharma"
    )

    // BUG-20: the UPI ID pattern took the bank's support e-mail for the payee.
    @Test fun `support e-mail in the footer is not the payee`() = assertDebit(
        "VM-HDFCBK",
        "Rs 750.00 debited from A/c XX12 on 05-11-26. Not you? Write to support@hdfcbank.net",
        750.0, "HDFC Bank debit"
    )

    // ── Amounts ──────────────────────────────────────────────────

    // BUG-03: the first amount (the balance) was taken.
    @Test fun `balance before the debit amount is not the amount`() = assertDebit(
        "VM-ICICIB",
        "A/c XX12: Avl Bal Rs 25,000.00 after debit of Rs 350.00 at ZOMATO on 05-11-26. Ref 530912345602",
        350.0, "ZOMATO"
    )

    @Test fun `available limit before or after the amount is not the amount`() {
        assertDebit("AX-SBICRD", "Avl Lmt: Rs 80,000.00. Rs 1,250.00 spent on your SBI Credit Card ending 1234 at DMART on 05-11-26.", 1250.0, "DMART")
        assertDebit("AX-AXISBK", "INR 2,00,000 available limit. INR 600.00 spent on Axis Bank Card XX12 at UBER on 05-11-26.", 600.0, "UBER")
    }

    // BUG-02: the INR available limit was saved as the amount of a USD charge.
    @Test fun `foreign currency charge with no rupee amount is not saved`() {
        assertIgnored("AX-HDFCBK", "USD 12.99 spent on HDFC Bank Card xx1234 at NETFLIX.COM on 2026-11-05:10:22:11. Avl Lmt: INR 1,23,456.00")
        assertIgnored("AX-HDFCBK", "Txn of USD 12.99 on HDFC Bank Card xx1234 at NETFLIX.COM on 05-11-26. Avl Lmt: INR 1,23,456.00")
    }

    // ── Not payments ─────────────────────────────────────────────

    // BUG-18: "debited" in the text overrode the credit.
    @Test fun `refund credited to the account`() = assertIgnored(
        "VM-HDFCBK",
        "Refund of Rs 1,200 for txn debited on 02-Nov-26 has been credited to your A/c XX12"
    )

    // BUG-17: merchant senders skipped the promo filter, so an order confirmation was a second expense.
    @Test fun `merchant order confirmation without an account reference`() {
        assertIgnored("AX-AMAZON", "Payment of Rs 499 successful for your order #402-1234567. Track at amzn.in/xyz")
        assertIgnored("VM-RZRPAY", "Payment of Rs 1,050 to ACME Store via Razorpay is successful. Payment ID pay_N4bXk2")
        // A payment app's own account still counts
        assertDebit("VM-PAYTMB", "Rs 120 debited from your Paytm Payments Bank a/c XX12 at CHAI POINT on 05-11-26", 120.0, "CHAI POINT")
    }

    // ── Helpers ──────────────────────────────────────────────────

    @Test fun `credit card detection`() {
        assertTrue(SmsParser.isCreditCardSms("Rs.799.00 spent on your SBI Credit Card ending 1234"))
        assertEquals("UPI", SmsParser.detectPaymentMethod("Rs.50 debited from a/c **1234"))
    }

    // BUG-04: "card ending 1234" made a debit card a credit card.
    @Test fun `debit card is not a credit card`() {
        val sms = "Rs 500.00 spent on your SBI Debit Card ending 1234 at DMART on 05-11-26"
        assertFalse(SmsParser.isCreditCardSms(sms))
        assertEquals("Debit Card", SmsParser.detectPaymentMethod(sms))
        assertEquals("Credit Card", SmsParser.detectPaymentMethod("Rs.799.00 spent on your SBI Credit Card ending 1234"))
        assertEquals("Credit Card", SmsParser.detectPaymentMethod("Rs.799.00 spent on Card ending 1234 at DMART"))
    }

    // B-23: a friend's "I paid Rs 500 to the shop" was saved as the user's expense: from an
    // unknown sender, an amount and a debit word were enough.
    @Test fun `a personal SMS from a phone number is not the user's payment`() {
        listOf("+919876543210", "9876543210", "+91 98765 43210", "919876543210").forEach { sender ->
            assertIgnored(sender, "I paid Rs 500 to the shop for you")
            assertIgnored(sender, "Sent Rs. 2,000 to mom yesterday, will send the rest on Monday")
            assertIgnored(sender, "Bro I spent INR 1500 on the tickets, pay me back")
        }
    }

    @Test fun `a bank SMS that names the account still counts from a phone number`() {
        // Some banks and forwarding services send from an ordinary number
        assertDebit("+919876543210", "Rs 349.00 debited from A/c XX1234 on 16-Oct-26 to BIGBASKET. UPI Ref 412345678901", 349.0, "BIGBASKET")
        assertNotNull(parse("9876543210", "INR 1,250.00 spent on Card ending 4321 at AMAZON on 12-Nov"))
    }

    @Test fun `short codes and sender names are not phone numbers`() {
        // An amount and a debit word, no account reference: kept as before for these senders
        assertNotNull(parse("567676", "Rs 120 paid to METRO RAIL via UPI Ref 998877"))
        assertNotNull(parse("VM-NOBANK", "Rs 120 paid to METRO RAIL via UPI Ref 998877"))
    }
}
