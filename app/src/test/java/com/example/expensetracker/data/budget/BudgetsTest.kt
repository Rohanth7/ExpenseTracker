package com.example.expensetracker.data.budget

import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import org.junit.Assert.assertEquals
import org.junit.Test

/** B-06: a subcategory's monthly limit was offered in the dialog, then ignored by alerts and the Overview. */
class BudgetsTest {
    private val food = Category(id = 1, name = "Food", monthlyLimit = 5000.0)
    private val coffee = Category(id = 2, name = "Coffee", monthlyLimit = 500.0, parentId = 1)
    private val groceries = Category(id = 3, name = "Groceries", parentId = 1)
    private val travel = Category(id = 4, name = "Travel")
    private val fuel = Category(id = 5, name = "Fuel", monthlyLimit = 3000.0, parentId = 4)
    private val rent = Category(id = 6, name = "Rent", monthlyLimit = 20000.0)
    private val categories = listOf(food, coffee, groceries, travel, fuel, rent)

    private fun spend(category: Category, amount: Double) = Expense(amount = amount, description = "x", categoryId = category.id, date = 1)

    @Test fun `an expense counts against its subcategory's limit and its category's`() {
        assertEquals(listOf(coffee, food), Budgets.limitsFor(coffee.id, categories))
        // Groceries has no limit of its own, Travel has none above Fuel
        assertEquals(listOf(food), Budgets.limitsFor(groceries.id, categories))
        assertEquals(listOf(fuel), Budgets.limitsFor(fuel.id, categories))
        assertEquals(listOf(food), Budgets.limitsFor(food.id, categories))
        assertEquals(emptyList<Category>(), Budgets.limitsFor(travel.id, categories))
        assertEquals(emptyList<Category>(), Budgets.limitsFor(99, categories))
    }

    @Test fun `a category's limit counts its subcategories, a subcategory's only itself`() {
        assertEquals(listOf(1L, 2L, 3L), Budgets.countedIds(food, categories))
        assertEquals(listOf(2L), Budgets.countedIds(coffee, categories))
    }

    @Test fun `every limit gets a row, subcategories under their category`() {
        val budgets = Budgets.of(categories, listOf(spend(coffee, 600.0), spend(groceries, 2000.0), spend(food, 100.0), spend(fuel, 1500.0), spend(rent, 20000.0)))
        assertEquals(listOf("Rent" to 20000.0, "Food" to 2700.0, "Food › Coffee" to 600.0, "Travel › Fuel" to 1500.0), budgets.map { it.name to it.spent })
        assertEquals(listOf(20000.0, 5000.0, 500.0, 3000.0), budgets.map { it.limit })
    }

    @Test fun `a limit nothing was spent against is still listed, at zero`() {
        assertEquals(listOf(0.0, 0.0, 0.0, 0.0), Budgets.of(categories, emptyList()).map { it.spent })
    }
}
