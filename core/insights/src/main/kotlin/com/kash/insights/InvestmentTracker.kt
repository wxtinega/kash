package com.kash.insights

import com.kash.parser.Money
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class EventSource { MPESA, BANK, FUND_MESSAGE, USER }

sealed interface InvestmentEvent {
    val date: LocalDate
    val source: EventSource

    data class Contribution(val amount: Money, override val date: LocalDate, override val source: EventSource) : InvestmentEvent
    data class Withdrawal(val amount: Money, override val date: LocalDate, override val source: EventSource) : InvestmentEvent
    /** The real value, from a monthly check-in ("What's your Britam balance today?") or a fund statement message. */
    data class ValueReport(val value: Money, override val date: LocalDate, override val source: EventSource) : InvestmentEvent
}

data class PeriodReturn(
    val from: LocalDate,
    val to: LocalDate,
    /** Growth after removing money put in or taken out during the period. */
    val gain: Money,
    /** Annualised, in basis points (1000 = 10.00% a year). Null if the period is too short to say. */
    val yearlyRateBps: Int?,
)

data class HoldingSummary(
    val scheme: Scheme,
    val contributed: Money,
    val withdrawn: Money,
    /** contributed − withdrawn */
    val netInvested: Money,
    val lastReport: InvestmentEvent.ValueReport?,
    /** Best current figure for net worth. */
    val value: Money,
    /** True when [value] is Kash's estimate rather than a reported figure. */
    val isEstimate: Boolean,
    /** value − netInvested: what the investment has earned overall (null until a real value is known). */
    val totalGain: Money?,
    val periods: List<PeriodReturn>,
    val nextCheckIn: LocalDate?,
)

/**
 * Keeps each holding's numbers honest:
 *  - contributions and withdrawals come from M-Pesa/bank messages automatically;
 *  - the fund's own messages fill gaps but never double-count (same amount within 3 days = same movement);
 *  - the real value comes from check-ins and fund statements; between them Kash estimates using the
 *    holding's OWN last observed growth rate, and only for schemes that accrue daily. Nothing is assumed
 *    for SACCOs, shares or T-bills.
 */
object InvestmentTracker {

    private const val SAME_MOVEMENT_DAYS = 3L

    fun summarize(
        scheme: Scheme,
        events: List<InvestmentEvent>,
        today: LocalDate,
        maturity: LocalDate? = null,
    ): HoldingSummary {
        val clean = dedupe(events).sortedBy { it.date }
        val contributed = clean.filterIsInstance<InvestmentEvent.Contribution>().sumMoney { it.amount }
        val withdrawn = clean.filterIsInstance<InvestmentEvent.Withdrawal>().sumMoney { it.amount }
        val net = contributed - withdrawn
        val reports = clean.filterIsInstance<InvestmentEvent.ValueReport>()
        val last = reports.lastOrNull()

        val periods = reports.zipWithNext { a, b -> period(a, b, clean) }

        val (value, estimate) = when {
            last == null -> net to true
            else -> {
                val flowsSince = netFlows(clean, last.date, today, includeStart = false)
                val accrued = if (scheme.growth == Growth.DAILY_ACCRUAL) accrual(last.value, periods.lastOrNull(), last.date, today) else Money.ZERO
                val v = last.value + flowsSince + accrued
                v to (flowsSince != Money.ZERO || accrued != Money.ZERO)
            }
        }

        return HoldingSummary(
            scheme = scheme,
            contributed = contributed,
            withdrawn = withdrawn,
            netInvested = net,
            lastReport = last,
            value = if (value.cents < 0) Money.ZERO else value,
            isEstimate = estimate,
            totalGain = last?.let { value - net },
            periods = periods,
            nextCheckIn = nextCheckIn(scheme, clean, maturity, today),
        )
    }

    /** A fund's confirmation message and the M-Pesa message describe the same movement; keep the M-Pesa one. */
    fun dedupe(events: List<InvestmentEvent>): List<InvestmentEvent> {
        val primary = events.filter { it.source != EventSource.FUND_MESSAGE || it is InvestmentEvent.ValueReport }
        val fromFund = events.filter { it.source == EventSource.FUND_MESSAGE && it !is InvestmentEvent.ValueReport }
        val extra = fromFund.filter { f -> primary.none { p -> sameMovement(p, f) } }
        return primary + extra
    }

    private fun sameMovement(a: InvestmentEvent, b: InvestmentEvent): Boolean {
        val close = Math.abs(ChronoUnit.DAYS.between(a.date, b.date)) <= SAME_MOVEMENT_DAYS
        return close && when {
            a is InvestmentEvent.Contribution && b is InvestmentEvent.Contribution -> a.amount == b.amount
            a is InvestmentEvent.Withdrawal && b is InvestmentEvent.Withdrawal -> a.amount == b.amount
            else -> false
        }
    }

    private fun netFlows(events: List<InvestmentEvent>, from: LocalDate, to: LocalDate, includeStart: Boolean): Money =
        events.filter { (if (includeStart) it.date >= from else it.date > from) && it.date <= to }
            .fold(Money.ZERO) { acc, e ->
                when (e) {
                    is InvestmentEvent.Contribution -> acc + e.amount
                    is InvestmentEvent.Withdrawal -> acc - e.amount
                    is InvestmentEvent.ValueReport -> acc
                }
            }

    private fun period(a: InvestmentEvent.ValueReport, b: InvestmentEvent.ValueReport, events: List<InvestmentEvent>): PeriodReturn {
        val flows = netFlows(events, a.date, b.date, includeStart = false)
        val gain = b.value - a.value - flows
        val days = ChronoUnit.DAYS.between(a.date, b.date)
        // Average money at work: start value plus half the period's net flows.
        val base = a.value.cents + flows.cents / 2
        val rate = if (days >= 7 && base > 0) (gain.cents.toDouble() / base * 365.0 / days * 10_000).toInt() else null
        return PeriodReturn(a.date, b.date, gain, rate)
    }

    /** Estimate growth since the last report at the holding's own last observed rate. Rounded down. */
    private fun accrual(value: Money, lastPeriod: PeriodReturn?, from: LocalDate, to: LocalDate): Money {
        val bps = lastPeriod?.yearlyRateBps ?: return Money.ZERO
        if (bps <= 0) return Money.ZERO
        val days = ChronoUnit.DAYS.between(from, to).coerceAtLeast(0)
        return Money((value.cents.toDouble() * bps / 10_000.0 * days / 365.0).toLong())
    }

    private fun nextCheckIn(scheme: Scheme, events: List<InvestmentEvent>, maturity: LocalDate?, today: LocalDate): LocalDate? {
        if (scheme.checkIn == CheckIn.AT_MATURITY) return maturity
        val months = scheme.checkIn.months ?: return null
        val anchor = events.lastOrNull { it is InvestmentEvent.ValueReport }?.date
            ?: events.firstOrNull()?.date ?: return null
        // Funds credit the month's interest in the first few days, so ask on the 5th.
        var next = anchor.plusMonths(months.toLong()).withDayOfMonth(5).let { if (it <= anchor) it.plusMonths(1) else it }
        // Missed check-ins aren't shown as overdue; ask at the next 5th from today.
        if (next < today) next = today.withDayOfMonth(5).let { if (it < today) it.plusMonths(1) else it }
        return next
    }

    private inline fun <T> List<T>.sumMoney(f: (T) -> Money): Money = fold(Money.ZERO) { acc, t -> acc + f(t) }
}
