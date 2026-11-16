package com.kash.insights

import com.kash.parser.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetsTest {
    private fun k(s: String) = Money.parseNumber(s)!!
    private val nov = YearMonth.of(2026, 11)
    private fun e(day: Int, amt: String, cat: Category?, month: Int = 11) =
        ReportEntry(LocalDate.of(2026, month, day), k(amt), Money.ZERO, false, cat, "X")

    private val report = MonthlyReports.build(
        listOf(
            e(1, "1,200", Category.FOOD), e(3, "1,800", Category.FOOD),     // food 3,000
            e(2, "500", Category.FUEL_TRANSPORT),                            // transport 500
            e(4, "2,600", Category.GOING_OUT),                               // going out 2,600
        ),
        nov,
    )

    @Test fun levelsFollowTheAgreedBands() {
        assertEquals(BudgetLevel.GREEN, Budgets.level(0))
        assertEquals(BudgetLevel.GREEN, Budgets.level(49))
        assertEquals(BudgetLevel.ORANGE, Budgets.level(50))
        assertEquals(BudgetLevel.ORANGE, Budgets.level(75))
        assertEquals(BudgetLevel.RED, Budgets.level(76))
        assertEquals(BudgetLevel.RED, Budgets.level(100))
        assertEquals(BudgetLevel.OVER, Budgets.level(101))
    }

    @Test fun statusWithPerDayAllowance() {
        val today = LocalDate.of(2026, 11, 16) // 15 days left including today
        val s = Budgets.status(
            listOf(Budget(Category.FOOD, k("5,000")), Budget(Category.FUEL_TRANSPORT, k("2,000")), Budget(Category.GOING_OUT, k("2,500")), Budget(null, k("10,000"))),
            report, today,
        )
        val food = s[0]
        assertEquals(k("3,000"), food.spent)
        assertEquals(60, food.percent)
        assertEquals(BudgetLevel.ORANGE, food.level)
        assertEquals(15, food.daysLeft)
        assertEquals(k("133"), food.perDay) // 2,000 / 15 = 133.33, rounded down to whole shillings

        assertEquals(BudgetLevel.GREEN, s[1].level) // 25%
        val out = s[2]
        assertEquals(BudgetLevel.OVER, out.level)
        assertEquals(Money.ZERO - k("100"), out.left)
        assertEquals(Money.ZERO, out.perDay)
        assertEquals(k("6,100"), s[3].spent) // all spending
        assertEquals(61, s[3].percent)
    }

    @Test fun exactlyAtLimitIsRedNotOver() {
        val s = Budgets.status(listOf(Budget(Category.FOOD, k("3,000"))), report, LocalDate.of(2026, 11, 30))
        assertEquals(100, s[0].percent)
        assertEquals(BudgetLevel.RED, s[0].level)
        assertEquals(1, s[0].daysLeft)
    }

    @Test fun suggestionsRoundUpLastMonth() {
        assertEquals(k("3,000"), Budgets.suggest(report, Category.FOOD))
        assertEquals(k("500"), Budgets.suggest(report, Category.FUEL_TRANSPORT))
        assertEquals(k("3,000"), Budgets.suggest(report, Category.GOING_OUT)) // 2,600 -> 3,000
        assertNull(Budgets.suggest(report, Category.HEALTH))
        assertNull(Budgets.suggest(null, Category.FOOD))
    }

    @Test fun alertsOncePerThreshold() {
        val s = Budgets.status(listOf(Budget(Category.FOOD, k("3,500")), Budget(Category.GOING_OUT, k("2,500"))), report, LocalDate.of(2026, 11, 16))
        val first = Budgets.alerts(s, nov, emptySet())
        assertEquals(listOf("2026-11:FOOD:76", "2026-11:GOING_OUT:over"), first.map { it.key }) // food 85%
        assertTrue(Budgets.alerts(s, nov, first.map { it.key }.toSet()).isEmpty())
        assertTrue(Budgets.budgetable.none { it == Category.SAVINGS || it == Category.MONEY_IN })
    }
}
