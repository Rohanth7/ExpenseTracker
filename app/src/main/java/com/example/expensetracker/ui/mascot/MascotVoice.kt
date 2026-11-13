package com.example.expensetracker.ui.mascot

import com.example.expensetracker.data.budget.Budget
import com.example.expensetracker.ui.util.fmtINR
import kotlin.math.ceil
import kotlin.math.floor

/** Her faces. The first four follow the budget, from plenty left to overspent; Asleep is the locked app. */
enum class MascotMood { Happy, Worried, Sad, Pleading, Asleep }

/** What she shows and says on Home. */
data class MascotSay(val mood: MascotMood, val line: String)

/**
 * One budget month as she sees it.
 *
 * @param income the month's forecast income; 0 when the user hasn't given one
 * @param stillDue bills and EMIs still to come this month
 * @param alertPercent the user's "warn me at" level, 50 to 95
 * @param daysLeft days left in the budget month, today included
 * @param monthStartDay the day the budget month starts on, 1 to 28
 * @param daysOver days since the month's money ran out; 0 on the day it happens
 * @param epochDay today, used only to choose between two wordings of the same message
 */
data class MascotMonth(
    val income: Double,
    val spent: Double,
    val stillDue: Double,
    val budgets: List<Budget>,
    val alertPercent: Int,
    val daysLeft: Int,
    val firstDay: Boolean,
    val monthStartDay: Int,
    val daysOver: Int = 0,
    val epochDay: Long,
)

/**
 * What she says and how she looks. Rules are checked top to bottom and the first that fits wins:
 * the whole month's money first, then single categories, then the calm lines. She says "we", never
 * blames, and looks her saddest only on the day the month's money runs out.
 */
object MascotVoice {

    fun say(month: MascotMonth): MascotSay = with(month) {
        val reset = "the ${ordinal(monthStartDay)}"
        val free = income - spent - stillDue
        if (income > 0) {
            if (free < 0) return MascotSay(
                if (daysOver == 0) MascotMood.Pleading else MascotMood.Sad,
                pick("We went over by ${rupees(ceil(-free))}. It'll be better next month.", "A bit over this month. Let's go easy till $reset.")
            )
            if (free == 0.0) return MascotSay(
                MascotMood.Sad,
                pick("That's all of it for this month.", "Nothing left to spend till $reset.")
            )
            val usedPercent = floor((spent + stillDue) / income * 100).toInt()
            if (usedPercent >= alertPercent) return MascotSay(
                MascotMood.Worried,
                pick(
                    "We're at $usedPercent%. Let's slow down a little.",
                    "${rupees(floor(free))} left for ${if (daysLeft <= 1) "today" else "$daysLeft days"}."
                )
            )
        }
        // Without an income she can't know the month is fine, so she doesn't say it is.
        val fine = if (income > 0) "We're doing fine. " else ""
        val over = budgets.filter { mood(it.spent, it.limit, alertPercent) == MascotMood.Pleading }.map { it.name }
        if (over.isNotEmpty()) return MascotSay(
            MascotMood.Happy,
            fine + when (over.size) {
                1 -> "${over[0]} is the only one to watch."
                2 -> "${over[0]} and ${over[1]} are the ones to watch."
                else -> "${over.size} categories are over their limits."
            }
        )
        val close = budgets.firstOrNull { mood(it.spent, it.limit, alertPercent).let { m -> m == MascotMood.Worried || m == MascotMood.Sad } }
        if (close != null) return MascotSay(
            MascotMood.Happy,
            (if (income > 0) "All good. " else "") +
                if (close.spent >= close.limit) "${close.name} has reached its limit." else "${close.name} is getting close to its limit."
        )
        if (firstDay) return MascotSay(MascotMood.Happy, "New month, fresh start.")
        if (income <= 0) return MascotSay(MascotMood.Happy, "Tell me your income and I'll work out what's free to spend.")
        MascotSay(
            MascotMood.Happy,
            pick("We're doing fine.", "${rupees(floor(free / daysLeft.coerceAtLeast(1)))} a day and plenty of room.")
        )
    }

    /** The face for one limit: beside a budget row, and for the month as a whole. */
    fun mood(spent: Double, limit: Double, alertPercent: Int): MascotMood = when {
        limit <= 0 -> MascotMood.Happy
        spent > limit -> MascotMood.Pleading
        spent == limit -> MascotMood.Sad
        spent >= limit * alertPercent / 100 -> MascotMood.Worried
        else -> MascotMood.Happy
    }

    // By the day, so the line holds still while the screen redraws and changes tomorrow.
    private fun MascotMonth.pick(vararg lines: String) = lines[Math.floorMod(epochDay, lines.size.toLong()).toInt()]

    private fun rupees(amount: Double) = "₹" + fmtINR(amount)

    private fun ordinal(day: Int): String = day.toString() + when {
        day % 100 in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
}
