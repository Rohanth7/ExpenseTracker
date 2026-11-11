package com.example.expensetracker.data.backup

import com.example.expensetracker.data.db.entity.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** B-15: after a v1/v2 restore the phone's bills and learned merchants pointed at ids that now meant other categories. */
class CategoryLinksTest {
    private val phone = listOf(
        Category(id = 3, name = "Rent"), Category(id = 4, name = "Pets"),
        Category(id = 5, name = "Travel"), Category(id = 6, name = "Fuel", parentId = 5),
        Category(id = 7, name = "Car"), Category(id = 9, name = "Fuel", parentId = 7)
    )
    private val file = listOf(
        Category(id = 3, name = "Food"), Category(id = 8, name = " rent "),
        Category(id = 20, name = "Car"), Category(id = 21, name = "FUEL", parentId = 20),
        Category(id = 30, name = "Travel")
    )
    private val links = CategoryLinks(phone, file)

    @Test fun `a link follows the category's name, not its id`() {
        // Id 3 was Rent and is Food in the file; Rent is 8 there
        assertEquals(8L, links.moved(3))
        assertEquals(30L, links.moved(5))
    }

    @Test fun `a subcategory is matched under the same parent`() {
        assertEquals(21L, links.moved(9))
        // Travel has no Fuel in the file, though Car does
        assertNull(links.moved(6))
    }

    @Test fun `a category the file doesn't have, or an id the phone never had, goes nowhere`() {
        assertNull(links.moved(4))
        assertNull(links.moved(99))
        assertNull(links.moved(-1))
    }

    @Test fun `the same categories keep their ids`() {
        val same = CategoryLinks(phone, phone)
        phone.forEach { assertEquals(it.id, same.moved(it.id)) }
    }
}
