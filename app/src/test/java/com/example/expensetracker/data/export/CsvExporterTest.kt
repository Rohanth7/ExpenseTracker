package com.example.expensetracker.data.export

import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CsvExporterTest {
    private val ist = TimeZone.getTimeZone("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int = 9, min: Int = 30) =
        Calendar.getInstance(ist).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis
    private val food = Category(id = 1, name = "Food")
    private val swiggy = Category(id = 2, name = "Swiggy", parentId = 1)
    private fun lines(csv: String) = csv.removePrefix(CsvExporter.BOM).split("\r\n").filter { it.isNotEmpty() }

    @Test fun `starts with a BOM and the header`() {
        val csv = CsvExporter.expensesToCsv(emptyList(), emptyList(), ist)
        assertTrue(csv.startsWith(CsvExporter.BOM))
        assertEquals(listOf("date,time,type,amount,description,category,subcategory,payment_method,tags,source"), lines(csv))
    }

    @Test fun `one row per expense, oldest first, in local time`() {
        val csv = CsvExporter.expensesToCsv(listOf(
            Expense(amount = 1500.0, description = "Rent", categoryId = 1, date = at(2026, 10, 5, 23, 45), source = "Recurring"),
            Expense(amount = 99.5, description = "Tea", categoryId = 1, date = at(2026, 10, 1)),
        ), listOf(food), ist)
        assertEquals("2026-10-01,09:30,expense,99.50,Tea,Food,,UPI,,Manual", lines(csv)[1])
        assertEquals("2026-10-05,23:45,expense,1500.00,Rent,Food,,UPI,,Recurring", lines(csv)[2])
    }

    @Test fun `subcategories split into category and subcategory`() {
        val csv = CsvExporter.expensesToCsv(
            listOf(Expense(amount = 250.0, description = "Lunch", categoryId = 2, date = at(2026, 10, 2))), listOf(food, swiggy), ist)
        assertTrue(lines(csv)[1].contains(",Food,Swiggy,"))
    }

    @Test fun `pending expenses say Pending`() {
        val csv = CsvExporter.expensesToCsv(
            listOf(Expense(amount = 10.0, description = "X", categoryId = -1, date = at(2026, 10, 2))), emptyList(), ist)
        assertTrue(lines(csv)[1].contains(",Pending,,"))
    }

    @Test fun `commas, quotes and newlines are quoted`() {
        val csv = CsvExporter.expensesToCsv(
            listOf(Expense(amount = 10.0, description = "Dinner, \"Taj\"\nhotel", categoryId = -1, date = at(2026, 10, 2))), emptyList(), ist)
        assertTrue(csv.contains("\"Dinner, \"\"Taj\"\"\nhotel\""))
    }

    @Test fun `amounts are plain numbers with two decimals`() {
        val csv = CsvExporter.expensesToCsv(
            listOf(Expense(amount = 125000.0, description = "Laptop", categoryId = -1, date = at(2026, 10, 2))), emptyList(), ist)
        assertTrue(lines(csv)[1].contains(",125000.00,"))
    }

    @Test fun `text that a spreadsheet would run as a formula is escaped`() {
        val csv = CsvExporter.expensesToCsv(listOf(
            Expense(amount = 10.0, description = "=HYPERLINK(\"http://x\",\"Refund\")", categoryId = -1, date = at(2026, 10, 2)),
            Expense(amount = 20.0, description = "+91 98765 43210", categoryId = -1, date = at(2026, 10, 3), tags = "@work"),
            Expense(amount = 30.0, description = "-Cash", categoryId = -1, date = at(2026, 10, 4)),
        ), emptyList(), ist)
        assertTrue(csv.contains(",\"'=HYPERLINK(\"\"http://x\"\",\"\"Refund\"\")\","))
        assertTrue(lines(csv)[2].contains(",'+91 98765 43210,"))
        assertTrue(lines(csv)[2].contains(",'@work,"))
        assertTrue(lines(csv)[3].startsWith("2026-10-04,09:30,expense,30.00,'-Cash,"))
    }
}
