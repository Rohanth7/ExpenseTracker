package com.example.expensetracker.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses its own prefs file; the app's settings aren't touched. */
@RunWith(AndroidJUnit4::class)
class MonthlyIncomeFlowTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = "monthly_income_flow_test"

    @Before fun setUp() = clear()
    @After fun tearDown() = clear()
    private fun clear() { context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun startsWithTheStoredValue() = runBlocking {
        PreferencesManager(context, file).monthlyIncome = 42000.0
        assertEquals(42000.0, PreferencesManager(context, file).monthlyIncomeFlow().first(), 0.0)
    }

    /** A restore or reset writes through its own PreferencesManager; a screen watching must see it. */
    @Test fun followsAWriteFromAnotherPreferencesManager() = runBlocking {
        val watched = PreferencesManager(context, file).monthlyIncomeFlow()
        assertEquals(0.0, watched.first(), 0.0)
        val seen = async { withTimeout(5000) { watched.first { it == 85000.0 } } }
        yield()
        PreferencesManager(context, file).monthlyIncome = 85000.0
        assertEquals(85000.0, seen.await(), 0.0)
    }

    @Test fun followsAResetToZero() = runBlocking {
        PreferencesManager(context, file).monthlyIncome = 85000.0
        val watched = PreferencesManager(context, file).monthlyIncomeFlow()
        val seen = async { withTimeout(5000) { watched.first { it == 0.0 } } }
        yield()
        PreferencesManager(context, file).monthlyIncome = 0.0
        assertEquals(0.0, seen.await(), 0.0)
    }
}
