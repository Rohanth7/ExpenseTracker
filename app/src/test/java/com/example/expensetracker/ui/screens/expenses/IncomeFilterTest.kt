package com.example.expensetracker.ui.screens.expenses

import com.example.expensetracker.data.db.entity.Income
import org.junit.Assert.assertEquals
import org.junit.Test

class IncomeFilterTest {
    private val salary = Income(id = 1, amount = 50000.0, description = "Salary", date = 3_000L, tags = "job")
    private val gig = Income(id = 2, amount = 2500.0, description = "Freelance logo", date = 2_000L, tags = "side")
    private val gift = Income(id = 3, amount = 1000.0, description = "Birthday", date = 1_000L)
    private val all = listOf(salary, gig, gift)

    private fun ids(query: String = "", range: Pair<Long, Long>? = null, min: Double? = null, max: Double? = null, byAmount: Boolean = false) =
        filterIncomes(all, query, range, min, max, byAmount).map { it.id }

    @Test fun `no filters keeps everything, newest first`() = assertEquals(listOf(1L, 2L, 3L), ids())
    @Test fun `search matches the description or tags, ignoring case`() {
        assertEquals(listOf(2L), ids("LOGO"))
        assertEquals(listOf(1L), ids("job"))
    }
    @Test fun `the date filter includes both ends`() = assertEquals(listOf(2L, 3L), ids(range = 1_000L to 2_000L))
    @Test fun `amount limits include both ends`() = assertEquals(listOf(2L, 3L), ids(min = 1000.0, max = 2500.0))
    @Test fun `sorting by amount puts the largest first`() = assertEquals(listOf(1L, 2L, 3L), ids(byAmount = true))
}
