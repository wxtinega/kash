package com.kash.insights

import com.kash.parser.Money
import java.time.LocalDate
import java.time.YearMonth

/** One money movement as the report sees it (already categorised by the app). */
data class ReportEntry(
    val date: LocalDate,
    val amount: Money,
    /** Transaction cost stated in the message. Always counted as spending (under Fees). */
    val fee: Money,
    val incoming: Boolean,
    /** Null = not sorted yet. */
    val category: Category?,
    val payee: String,
)

data class CategoryTotal(val category: Category?, val amount: Money, val count: Int, val share: Double)
/** Spending for days [firstDay]..[lastDay] (1-based) of the month. */
data class WeekTotal(val firstDay: Int, val lastDay: Int, val amount: Money)

data class PayeeTotal(val payee: String, val amount: Money, val count: Int)

data class MonthlyReport(
    val month: YearMonth,
    /** Real income: excludes moves between your accounts and money taken out of savings. */
    val moneyIn: Money,
    /** Everything spent, including transaction costs. Excludes savings and transfers. */
    val spent: Money,
    val saved: Money,
    val fees: Money,
    val byCategory: List<CategoryTotal>,
    val topPayees: List<PayeeTotal>,
    /** Spending per day of the month (index 0 = the 1st). */
    val daily: List<Money>,
    val biggestDay: Pair<LocalDate, Money>?,
    val unsortedCount: Int,
    /** Same figures for the month before, for comparison (null if no data). */
    val previousSpent: Money?,
    val transactionCount: Int,
    /** Plain-language observations, most useful first. */
    val highlights: List<String>,
) {
    val net: Money get() = moneyIn - spent - saved
    val isEmpty: Boolean get() = transactionCount == 0

    /** Days 1–7, 8–14, 15–21, 22–28, 29–end: readable totals under the daily chart. */
    val weeks: List<WeekTotal>
        get() = daily.chunked(7).mapIndexed { i, chunk ->
            WeekTotal(i * 7 + 1, i * 7 + chunk.size, chunk.fold(Money.ZERO) { a, m -> a + m })
        }

    /** The days worth labelling on a small chart: the [n] biggest spending days, in date order (0-based). */
    fun topDays(n: Int): List<Int> =
        daily.withIndex().filter { it.value.isPositive }.sortedByDescending { it.value.cents }.take(n).map { it.index }.sorted()
}

object MonthlyReports {

    fun build(entries: List<ReportEntry>, month: YearMonth): MonthlyReport {
        val inMonth = entries.filter { YearMonth.from(it.date) == month }
        val prev = entries.filter { YearMonth.from(it.date) == month.minusMonths(1) }

        val moneyIn = inMonth.filter { it.incoming && it.category == Category.MONEY_IN }.sum { it.amount }
        val saved = inMonth.filter { !it.incoming && it.category == Category.SAVINGS }.sum { it.amount }
        val fees = inMonth.sum { it.fee }

        // Spending lines: outgoing, not savings/transfers. Costs are added to Fees, not to the payee.
        val spendingLines = inMonth.filter { !it.incoming && it.category?.isSpending != false }
        val spent = spendingLines.sum { it.amount } + fees

        val totals = spendingLines.groupBy { it.category }
            .mapValues { (_, v) -> v.sum { it.amount } to v.size }
            .toMutableMap()
        if (fees.isPositive) {
            val (a, n) = totals[Category.FEES] ?: (Money.ZERO to 0)
            totals[Category.FEES] = (a + fees) to (n + inMonth.count { it.fee.isPositive })
        }
        val byCategory = totals.map { (cat, v) ->
            CategoryTotal(cat, v.first, v.second, if (spent.cents > 0) v.first.cents.toDouble() / spent.cents else 0.0)
        }.filter { it.amount.isPositive }.sortedByDescending { it.amount }

        val topPayees = spendingLines.groupBy { it.payee }
            .map { (p, v) -> PayeeTotal(p, v.sum { it.amount }, v.size) }
            .sortedByDescending { it.amount }
            .take(5)

        val days = month.lengthOfMonth()
        val daily = (1..days).map { d ->
            val day = month.atDay(d)
            spendingLines.filter { it.date == day }.sum { it.amount + it.fee }
        }
        val biggestIdx = daily.indices.maxByOrNull { daily[it].cents }
        val biggestDay = biggestIdx?.takeIf { daily[it].isPositive }?.let { month.atDay(it + 1) to daily[it] }

        val prevSpent = if (prev.isEmpty()) null else
            prev.filter { !it.incoming && it.category?.isSpending != false }.sum { it.amount } + prev.sum { it.fee }

        val unsorted = inMonth.count { !it.incoming && it.category == null }

        return MonthlyReport(
            month = month,
            moneyIn = moneyIn,
            spent = spent,
            saved = saved,
            fees = fees,
            byCategory = byCategory,
            topPayees = topPayees,
            daily = daily,
            biggestDay = biggestDay,
            unsortedCount = unsorted,
            previousSpent = prevSpent,
            transactionCount = inMonth.size,
            highlights = highlights(byCategory, spent, prevSpent, fees, moneyIn, saved, inMonth, unsorted, biggestDay),
        )
    }

    private fun highlights(
        cats: List<CategoryTotal>,
        spent: Money,
        prevSpent: Money?,
        fees: Money,
        moneyIn: Money,
        saved: Money,
        inMonth: List<ReportEntry>,
        unsorted: Int,
        biggestDay: Pair<LocalDate, Money>?,
    ): List<String> = buildList {
        cats.firstOrNull { it.category != null }?.let {
            add("Most of your money went to ${it.category!!.label}: ${it.amount.format()} (${pct(it.share)} of spending).")
        }
        if (prevSpent != null && prevSpent.isPositive) {
            val diff = spent - prevSpent
            val change = Math.abs(diff.cents.toDouble() / prevSpent.cents)
            if (change >= 0.05) {
                add(
                    if (diff.cents > 0) "You spent ${diff.format()} more than last month (${pct(change)} up)."
                    else "You spent ${(-diff).format()} less than last month (${pct(change)} down).",
                )
            }
        }
        val fuliza = inMonth.filter { it.category == Category.FEES || it.category == Category.LOAN_REPAYMENT }
        if (fees.isPositive) add("Transaction costs and charges came to ${fees.format()}.")
        if (fuliza.any { it.payee.contains("Fuliza", ignoreCase = true) }) {
            add("You used Fuliza this month. Its fees add up; topping up before you run out avoids them.")
        }
        if (saved.isPositive) add("You saved ${saved.format()}. Nice.")
        if (moneyIn.isPositive && spent > moneyIn) add("You spent more than came in this month.")
        biggestDay?.let { add("Your biggest spending day was ${it.first.dayOfMonth}${suffix(it.first.dayOfMonth)} (${it.second.format()}).") }
        if (unsorted > 0) add("$unsorted payment${if (unsorted == 1) " isn't" else "s aren't"} sorted yet. Sorting them makes this report more accurate.")
    }

    private fun pct(x: Double) = "${Math.round(x * 100)}%"
    private fun suffix(d: Int) = if (d in 11..13) "th" else when (d % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    private inline fun <T> List<T>.sum(f: (T) -> Money): Money = fold(Money.ZERO) { acc, t -> acc + f(t) }
}
