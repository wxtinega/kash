package com.kash.insights

import com.kash.parser.Money
import java.time.LocalDate
import java.time.YearMonth

/** A monthly limit for one category, or for all spending when [category] is null. */
data class Budget(val category: Category?, val limit: Money) {
    val label: String get() = category?.label ?: "All spending"
}

/**
 * How much of a budget is used:
 *  - GREEN  0–49%   on track
 *  - ORANGE 50–75%  slow down
 *  - RED    76–100% almost out
 *  - OVER   above 100%
 */
enum class BudgetLevel(val word: String) { GREEN("On track"), ORANGE("Slow down"), RED("Almost out"), OVER("Over budget") }

data class BudgetStatus(
    val budget: Budget,
    val spent: Money,
    /** Whole percent used, e.g. 63. Can go above 100. */
    val percent: Int,
    val level: BudgetLevel,
    /** Limit minus spent; negative when over. */
    val left: Money,
    /** What can still be spent each remaining day, today included. Zero when nothing is left. */
    val perDay: Money,
    val daysLeft: Int,
)

/** A one-off heads-up the first time a budget passes 75% or 100% in a month. */
data class BudgetAlert(val key: String, val title: String, val body: String)

object Budgets {

    /** Categories a budget can be set for: anything that counts as spending. */
    val budgetable: List<Category> = Category.entries.filter { it.isSpending }

    fun level(percent: Int): BudgetLevel = when {
        percent > 100 -> BudgetLevel.OVER
        percent >= 76 -> BudgetLevel.RED
        percent >= 50 -> BudgetLevel.ORANGE
        else -> BudgetLevel.GREEN
    }

    fun spentOn(report: MonthlyReport, category: Category?): Money =
        if (category == null) report.spent
        else report.byCategory.firstOrNull { it.category == category }?.amount ?: Money.ZERO

    /** Status of each budget for [report]'s month, as of [today]. */
    fun status(budgets: List<Budget>, report: MonthlyReport, today: LocalDate): List<BudgetStatus> {
        val month = report.month
        val daysLeft = when {
            YearMonth.from(today) == month -> month.lengthOfMonth() - today.dayOfMonth + 1
            YearMonth.from(today).isBefore(month) -> month.lengthOfMonth()
            else -> 0
        }
        return budgets.filter { it.limit.isPositive }.map { b ->
            val spent = spentOn(report, b.category)
            // floor, so 75.9% still reads 75% (orange) and only 100.x% counts as over
            val percent = ((spent.cents.toDouble() / b.limit.cents) * 100).toInt().let {
                if (it == 100 && spent.cents > b.limit.cents) 101 else it
            }
            val left = b.limit - spent
            val perDay = if (daysLeft <= 0 || !left.isPositive) Money.ZERO else Money(left.cents / daysLeft / 100 * 100)
            BudgetStatus(b, spent, percent, level(percent), left, perDay, daysLeft)
        }
    }

    /**
     * A suggested limit, from what was spent last month: rounded up to the next 500
     * (or 100 for small amounts). Null when there's nothing to go on.
     */
    fun suggest(lastMonth: MonthlyReport?, category: Category?): Money? {
        lastMonth ?: return null
        val spent = spentOn(lastMonth, category)
        if (!spent.isPositive) return null
        val shillings = spent.cents / 100
        val step = if (shillings < 2_000) 100 else 500
        val rounded = ((shillings + step - 1) / step) * step
        return Money(rounded * 100)
    }

    /** Alerts newly due (not in [alreadySent]). At most one per budget per threshold per month. */
    fun alerts(statuses: List<BudgetStatus>, month: YearMonth, alreadySent: Set<String>): List<BudgetAlert> =
        statuses.mapNotNull { s ->
            val id = "$month:${s.budget.category?.name ?: "ALL"}"
            when {
                s.level == BudgetLevel.OVER && "$id:over" !in alreadySent -> BudgetAlert(
                    "$id:over",
                    "${s.budget.label}: over budget",
                    "You've spent ${s.spent.format()} of ${s.budget.limit.format()} this month.",
                )
                s.level == BudgetLevel.RED && "$id:76" !in alreadySent -> BudgetAlert(
                    "$id:76",
                    "${s.budget.label}: ${s.percent}% used",
                    "${s.left.format()} left for ${s.daysLeft} day${if (s.daysLeft == 1) "" else "s"}, about ${s.perDay.format()} a day.",
                )
                else -> null
            }
        }
}
