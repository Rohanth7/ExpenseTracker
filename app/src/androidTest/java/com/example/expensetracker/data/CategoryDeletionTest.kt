package com.example.expensetracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.dao.MappingHelper
import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.MerchantMapping
import com.example.expensetracker.data.repository.CategoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device against a throwaway in-memory database; the app's real data isn't touched. */
@RunWith(AndroidJUnit4::class)
class CategoryDeletionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var repo: CategoryRepository
    private var foodId = 0L
    private var transportId = 0L

    @Before fun setUp() { runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = CategoryRepository(db.categoryDao(), db.expenseDao())
        foodId = db.categoryDao().insert(Category(name = "Food"))
        transportId = db.categoryDao().insert(Category(name = "Transport"))
        db.merchantMappingDao().insert(MerchantMapping("SWIGGY", foodId))
        db.merchantMappingDao().insert(MerchantMapping("UBER INDIA", transportId))
        db.billDao().insert(Bill(name = "Zomato Gold", amount = 99.0, dueDayOfMonth = 3, categoryId = foodId))
        db.expenseDao().insert(Expense(amount = 450.0, description = "SWIGGY", categoryId = foodId))
    } }

    @After fun tearDown() = db.close()

    @Test fun deletingACategoryForgetsItsLearnedMerchants() = runBlocking {
        repo.delete(db.categoryDao().getById(foodId)!!)
        assertEquals(listOf("UBER INDIA"), db.merchantMappingDao().getAllMappings().map { it.merchantName })
    }

    @Test fun deletingACategoryLeavesItsBillsUncategorized() = runBlocking {
        repo.delete(db.categoryDao().getById(foodId)!!)
        assertEquals(-1L, db.billDao().getAll().first().single().categoryId)
    }

    @Test fun deletingACategoryMovesItsExpensesToPending() = runBlocking {
        repo.delete(db.categoryDao().getById(foodId)!!)
        assertEquals(-1L, db.expenseDao().getById(1)!!.categoryId)
    }

    @Test fun aNewPaymentFromAForgottenMerchantStaysPending() = runBlocking {
        repo.delete(db.categoryDao().getById(foodId)!!)
        assertNull(MappingHelper.getCategoryId(db, "SWIGGY"))
    }

    @Test fun ruleLeftOverFromAnEarlierDeleteIsIgnored() = runBlocking {
        // Installs that deleted a category before this fix still have rules pointing at it.
        db.merchantMappingDao().insert(MerchantMapping("ZOMATO", 999L))
        assertNull(MappingHelper.getCategoryId(db, "ZOMATO"))
        assertNull(MappingHelper.getCategoryId(db, "ZOMATO ORDER 1234"))
        assertEquals(transportId, MappingHelper.getCategoryId(db, "UBER INDIA"))
    }

}
