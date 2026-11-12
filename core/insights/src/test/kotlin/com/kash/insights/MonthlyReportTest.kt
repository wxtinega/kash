package com.kash.insights

import com.kash.parser.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthlyReportTest {
    private fun k(s: String) = Money.parseNumber(s)!!
    private val oct = YearMonth.of(2026, 10)
    private fun e(day: Int, amt: String, cat: Category?, payee: String, incoming: Boolean = false, fee: String = "0", month: Int = 10) =
        ReportEntry(LocalDate.of(2026, month, day), k(amt), k(fee), incoming, cat, payee)

    private val entries = listOf(
        e(1, "5,000", Category.MONEY_IN, "PAUL MOKAYA", incoming = true),
        e(1, "1,000", Category.SAVINGS, "BRITAM"),
        e(2, "500", Category.UTILITIES, "KPLC PREPAID"),
        e(3, "640", Category.SHOPPING, "NAIVAS"),
        e(5, "250", Category.FOOD, "MAMA OLIECH CAFE"),
        e(6, "300", Category.FOOD, "MAMA OLIECH CAFE", fee = "7"),
        e(7, "320", Category.TRANSFER, "Co-op Bank"),
        e(8, "80", null, "Chron Investments"),
        e(9, "2,000", Category.SAVINGS, "CIC", incoming = true), // withdrawal from savings: not income
        // September, for comparison
        e(10, "1,000", Category.FOOD, "X", month = 9),
    )

    @Test fun totalsSeparateIncomeSpendingSavingsAndTransfers() {
        val r = MonthlyReports.build(entries, oct)
        assertEquals(k("5,000"), r.moneyIn) // savings withdrawal not counted as income
        assertEquals(k("1,000"), r.saved)
        assertEquals(k("7"), r.fees)
        // 500 + 640 + 250 + 300 + 80 (unsorted) + 7 fee; transfer and savings excluded
        assertEquals(k("1,777"), r.spent)
        assertEquals(1, r.unsortedCount)
    }

    @Test fun categoriesRankedWithFeesAsTheirOwnLine() {
        val r = MonthlyReports.build(entries, oct)
        assertEquals(Category.SHOPPING, r.byCategory[0].category)
        assertEquals(k("640"), r.byCategory[0].amount)
        assertEquals(k("550"), r.byCategory.first { it.category == Category.FOOD }.amount)
        assertEquals(k("7"), r.byCategory.first { it.category == Category.FEES }.amount)
        assertTrue(r.byCategory.none { it.category == Category.TRANSFER || it.category == Category.SAVINGS })
        assertEquals(1.0, r.byCategory.sumOf { it.share }, 0.0001)
    }

    @Test fun topPayeesAndDailySeries() {
        val r = MonthlyReports.build(entries, oct)
        assertEquals("NAIVAS", r.topPayees[0].payee)
        assertEquals(PayeeTotal("MAMA OLIECH CAFE", k("550"), 2), r.topPayees[1])
        assertEquals(31, r.daily.size)
        assertEquals(k("307"), r.daily[5]) // 6th: 300 + 7 cost
        assertEquals(LocalDate.of(2026, 10, 3) to k("640"), r.biggestDay)
    }

    @Test fun weeklyTotalsAndLabelledDays() {
        val r = MonthlyReports.build(entries, oct)
        assertEquals(5, r.weeks.size)
        assertEquals(WeekTotal(1, 7, k("1,697")), r.weeks[0]) // 500 + 640 + 250 + 307
        assertEquals(WeekTotal(8, 14, k("80")), r.weeks[1])
        assertEquals(WeekTotal(29, 31, Money.ZERO), r.weeks[4])
        assertEquals(r.spent, r.weeks.fold(Money.ZERO) { a, w -> a + w.amount })
        assertEquals(listOf(1, 2, 5), r.topDays(3)) // 2nd 500, 3rd 640, 6th 307 (0-based), in date order
    }

    @Test fun compactChartLabels() {
        assertEquals("850", k("850").compact())
        assertEquals("1k", k("1,000").compact())
        assertEquals("2.5k", k("2,540").compact())
        assertEquals("12k", k("12,400").compact())
        assertEquals("1M", k("999,600").compact())
        assertEquals("1.2M", k("1,230,000").compact())
    }

    @Test fun comparesWithPreviousMonth() {
        val r = MonthlyReports.build(entries, oct)
        assertEquals(k("1,000"), r.previousSpent)
        assertTrue(r.highlights.any { it.startsWith("You spent KSh 777.00 more than last month") })
        assertTrue(r.highlights.first().startsWith("Most of your money went to Shopping"))
    }

    @Test fun emptyMonth() {
        val r = MonthlyReports.build(emptyList(), oct)
        assertTrue(r.isEmpty)
        assertNull(r.biggestDay)
        assertTrue(r.byCategory.isEmpty())
    }
}
