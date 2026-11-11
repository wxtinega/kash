package com.kash.insights

import com.kash.parser.Money
import com.kash.parser.Sanitizer
import java.time.LocalDate

/**
 * Reads messages from fund managers, SACCOs and similar (redemptions, top-up confirmations,
 * balance statements). Every manager words these differently, so this looks for the common shapes.
 *
 * Safety: fund sender names aren't known in advance, so these messages are used only from a
 * sender the user has confirmed ("Yes, this is my fund"). They update investment records only,
 * never M-Pesa or bank balances.
 */
object FundMessageParser {

    private const val AMT = """(?:Kes|Ksh|KShs)\.?\s?((?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?)"""

    private val REDEMPTION = Regex(
        """(?:redemption|withdrawal)(?: request)?(?: of| for)?\s*$AMT.{0,80}?(?:processed|paid|sent|successful|credited)""",
        RegexOption.IGNORE_CASE,
    )
    private val CONTRIBUTION = Regex(
        """(?:top[- ]?up|deposit|investment|contribution|payment)(?: of)?\s*$AMT.{0,80}?(?:received|successful|credited|invested|allocated)""",
        RegexOption.IGNORE_CASE,
    )
    private val CONTRIBUTION_ALT = Regex(
        """(?:received|credited)(?: your)?(?: top[- ]?up| deposit| investment| contribution| payment)? of\s*$AMT""",
        RegexOption.IGNORE_CASE,
    )
    private val BALANCE = Regex(
        """(?:balance|portfolio value|account value|valuation|market value)(?: is| of| as at [^:]{1,30})?\s*:?\s*$AMT""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(body: String, date: LocalDate): List<InvestmentEvent> {
        val text = Sanitizer.cleanBody(body) ?: return emptyList()
        val out = mutableListOf<InvestmentEvent>()
        val src = EventSource.FUND_MESSAGE

        REDEMPTION.find(text)?.let { m -> Money.parseNumber(m.groupValues[1])?.let { out += InvestmentEvent.Withdrawal(it, date, src) } }
        if (out.isEmpty()) {
            (CONTRIBUTION.find(text) ?: CONTRIBUTION_ALT.find(text))?.let { m ->
                Money.parseNumber(m.groupValues[1])?.let { out += InvestmentEvent.Contribution(it, date, src) }
            }
        }
        BALANCE.find(text)?.let { m -> Money.parseNumber(m.groupValues[1])?.let { out += InvestmentEvent.ValueReport(it, date, src) } }
        return out
    }
}
