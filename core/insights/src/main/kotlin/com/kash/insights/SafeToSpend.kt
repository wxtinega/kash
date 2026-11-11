package com.kash.insights

import com.kash.parser.Money
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Helps BEFORE spending: "You can spend KSh 340 a day until your next stipend."
 *
 * available = money in M-Pesa + banks
 *           − bills still due before payday (rent, Wi-Fi, HELB…)
 *           − what the user has set aside for savings goals
 *           − outstanding Fuliza (it's already spent money)
 * per day   = available ÷ days left until next income (today included)
 */
object SafeToSpend {

    data class Input(
        val balances: Money,
        val billsDueBeforePayday: Money,
        val savingsSetAside: Money,
        val fulizaOutstanding: Money,
        val today: LocalDate,
        val nextIncome: LocalDate,
        val spentToday: Money = Money.ZERO,
    )

    data class Result(
        val available: Money,
        val perDay: Money,
        val daysLeft: Long,
        /** Left of today's allowance (negative = overspent today). */
        val leftToday: Money,
        val status: Status,
    )

    enum class Status { COMFORTABLE, TIGHT, SHORT }

    fun compute(i: Input): Result {
        val daysLeft = ChronoUnit.DAYS.between(i.today, i.nextIncome).coerceAtLeast(1)
        // Today's spending is added back so the daily figure doesn't shrink while you spend.
        val available = i.balances + i.spentToday - i.billsDueBeforePayday - i.savingsSetAside - i.fulizaOutstanding
        val perDay = if (available.cents <= 0) Money.ZERO else Money(available.cents / daysLeft) // round down
        val status = when {
            available.cents <= 0 -> Status.SHORT
            perDay < Money.ofShillings(150) -> Status.TIGHT
            else -> Status.COMFORTABLE
        }
        return Result(available, perDay, daysLeft, perDay - i.spentToday, status)
    }
}
