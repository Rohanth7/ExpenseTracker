package com.example.expensetracker.ui.mascot

import com.example.expensetracker.data.budget.Budget
import com.example.expensetracker.data.db.entity.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MascotVoiceTest {
    private fun budget(name: String, spent: Double, limit: Double) = Budget(Category(id = name.hashCode().toLong(), name = name, monthlyLimit = limit), null, spent)

    private fun month(
        income: Double = 85_000.0, spent: Double = 41_280.0, stillDue: Double = 21_900.0,
        budgets: List<Budget> = emptyList(), alertPercent: Int = 80, daysLeft: Int = 18,
        firstDay: Boolean = false, monthStartDay: Int = 1, daysOver: Int = 0, epochDay: Long = 0,
    ) = MascotMonth(income, spent, stillDue, budgets, alertPercent, daysLeft, firstDay, monthStartDay, daysOver, epochDay)

    private fun bothLines(month: MascotMonth) = listOf(MascotVoice.say(month.copy(epochDay = 0)), MascotVoice.say(month.copy(epochDay = 1)))

    @Test fun aCalmMonthIsHappyAndSaysWhatADayAllows() {
        val (first, second) = bothLines(month())
        assertEquals(MascotSay(MascotMood.Happy, "We're doing fine."), first)
        // 21,820 free over 18 days is 1,212.22 a day; she rounds down, never up.
        assertEquals(MascotSay(MascotMood.Happy, "₹1,212 a day and plenty of room."), second)
    }

    @Test fun pastTheAlertLevelSheWorries() {
        val (first, second) = bothLines(month(spent = 50_000.0, stillDue = 20_000.0, daysLeft = 9))
        assertEquals(MascotSay(MascotMood.Worried, "We're at 82%. Let's slow down a little."), first)
        assertEquals(MascotSay(MascotMood.Worried, "₹15,000 left for 9 days."), second)
    }

    @Test fun theAlertLevelIsTheUsersOwn() {
        val at82 = month(spent = 50_000.0, stillDue = 20_000.0)
        assertEquals(MascotMood.Worried, MascotVoice.say(at82.copy(alertPercent = 80)).mood)
        assertEquals(MascotMood.Happy, MascotVoice.say(at82.copy(alertPercent = 90)).mood)
    }

    @Test fun onTheLastDaySheSaysToday() {
        assertEquals("₹15,000 left for today.", MascotVoice.say(month(spent = 50_000.0, stillDue = 20_000.0, daysLeft = 1, epochDay = 1)).line)
    }

    @Test fun whenTheMoneyIsExactlyUsedSheIsSad() {
        val (first, second) = bothLines(month(spent = 63_100.0))
        assertEquals(MascotSay(MascotMood.Sad, "That's all of it for this month."), first)
        assertEquals(MascotSay(MascotMood.Sad, "Nothing left to spend till the 1st."), second)
    }

    @Test fun overTheMonthSheLooksHerSaddestOnlyOnTheDayItHappens() {
        val over = month(spent = 64_440.0)
        assertEquals(MascotSay(MascotMood.Pleading, "We went over by ₹1,340. It'll be better next month."), MascotVoice.say(over))
        assertEquals(MascotSay(MascotMood.Sad, "We went over by ₹1,340. It'll be better next month."), MascotVoice.say(over.copy(daysOver = 1)))
        assertEquals("A bit over this month. Let's go easy till the 1st.", MascotVoice.say(over.copy(epochDay = 1)).line)
    }

    @Test fun theResetDayIsTheUsersMonthStart() {
        assertEquals("Nothing left to spend till the 25th.", MascotVoice.say(month(spent = 63_100.0, monthStartDay = 25, epochDay = 1)).line)
        assertEquals("A bit over this month. Let's go easy till the 22nd.", MascotVoice.say(month(spent = 70_000.0, monthStartDay = 22, epochDay = 1)).line)
        assertEquals("Nothing left to spend till the 11th.", MascotVoice.say(month(spent = 63_100.0, monthStartDay = 11, epochDay = 1)).line)
    }

    @Test fun theMonthComesBeforeAnyCategory() {
        val say = MascotVoice.say(month(spent = 64_440.0, budgets = listOf(budget("Shopping", 7_420.0, 6_000.0))))
        assertEquals(MascotMood.Pleading, say.mood)
        assertFalse(say.line.contains("Shopping"))
    }

    @Test fun aFineMonthPointsAtTheCategoriesThatAreOver() {
        val shopping = budget("Shopping", 7_420.0, 6_000.0)
        val food = budget("Food", 12_500.0, 12_000.0)
        val health = budget("Health", 2_100.0, 2_000.0)
        assertEquals(MascotSay(MascotMood.Happy, "We're doing fine. Shopping is the only one to watch."), MascotVoice.say(month(budgets = listOf(shopping))))
        assertEquals("We're doing fine. Shopping and Food are the ones to watch.", MascotVoice.say(month(budgets = listOf(shopping, food))).line)
        assertEquals("We're doing fine. 3 categories are over their limits.", MascotVoice.say(month(budgets = listOf(shopping, food, health))).line)
    }

    @Test fun anOverCategoryComesBeforeACloseOne() {
        val line = MascotVoice.say(month(budgets = listOf(budget("Food", 9_840.0, 12_000.0), budget("Shopping", 7_420.0, 6_000.0)))).line
        assertEquals("We're doing fine. Shopping is the only one to watch.", line)
    }

    @Test fun aCategoryNearOrAtItsLimitIsNamed() {
        assertEquals(MascotSay(MascotMood.Happy, "All good. Food is getting close to its limit."), MascotVoice.say(month(budgets = listOf(budget("Food", 9_840.0, 12_000.0)))))
        assertEquals("All good. Rent has reached its limit.", MascotVoice.say(month(budgets = listOf(budget("Rent", 25_000.0, 25_000.0)))).line)
    }

    @Test fun theFirstDayOfTheMonthIsAFreshStart() {
        assertEquals(MascotSay(MascotMood.Happy, "New month, fresh start."), MascotVoice.say(month(spent = 0.0, firstDay = true)))
    }

    @Test fun withoutAnIncomeSheNeverClaimsTheMonthIsFine() {
        val noIncome = month(income = 0.0, spent = 90_000.0)
        assertEquals(MascotSay(MascotMood.Happy, "Tell me your income and I'll work out what's free to spend."), MascotVoice.say(noIncome))
        assertEquals("Shopping is the only one to watch.", MascotVoice.say(noIncome.copy(budgets = listOf(budget("Shopping", 7_420.0, 6_000.0)))).line)
        assertEquals("Food is getting close to its limit.", MascotVoice.say(noIncome.copy(budgets = listOf(budget("Food", 9_840.0, 12_000.0)))).line)
    }

    @Test fun theSameDayAlwaysGivesTheSameLine() {
        val calm = month(epochDay = 20_500)
        assertEquals(MascotVoice.say(calm), MascotVoice.say(calm))
    }

    @Test fun sheNeverBlamesOrShouts() {
        val months = listOf(
            month(), month(spent = 50_000.0, stillDue = 20_000.0), month(spent = 63_100.0), month(spent = 64_440.0),
            month(budgets = listOf(budget("Shopping", 7_420.0, 6_000.0))), month(income = 0.0), month(firstDay = true),
        )
        for (line in months.flatMap(::bothLines).map { it.line }) {
            assertFalse(line, line.contains("!"))
            assertFalse(line, Regex("\\byou\\b", RegexOption.IGNORE_CASE).containsMatchIn(line))
        }
    }

    @Test fun oneLimitHasFourMoods() {
        assertEquals(MascotMood.Happy, MascotVoice.mood(7_900.0, 10_000.0, 80))
        assertEquals(MascotMood.Worried, MascotVoice.mood(8_000.0, 10_000.0, 80))
        assertEquals(MascotMood.Sad, MascotVoice.mood(10_000.0, 10_000.0, 80))
        assertEquals(MascotMood.Pleading, MascotVoice.mood(10_000.5, 10_000.0, 80))
        assertEquals(MascotMood.Happy, MascotVoice.mood(500.0, 0.0, 80))
    }
}
