package com.kash.insights

import com.kash.parser.Money
import com.kash.parser.ParseOutcome
import com.kash.parser.RawSms
import com.kash.parser.SmsPipeline
import com.kash.parser.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

private fun k(s: String) = Money.parseNumber(s)!!
private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)
private fun tx(body: String): Transaction =
    (SmsPipeline().process(RawSms("MPESA", body, Instant.parse("2026-11-11T08:00:00Z"))) as ParseOutcome.Parsed).tx

private fun paybill(name: String, amount: String) = tx(
    "UL9GG9GGGG Confirmed. Ksh$amount sent to $name for account 12345 on 12/11/26 at 4:00 PM " +
        "New M-PESA balance is Ksh9,000.00. Transaction cost, Ksh0.00.",
)
private fun till(name: String, amount: String) = tx(
    "UL9HH9HHHH Confirmed. Ksh$amount paid to $name. on 12/11/26 at 1:00 PM.New M-PESA balance is Ksh9,000.00. Transaction cost, Ksh0.00.",
)
private fun receivedFrom(who: String, amount: String) = tx(
    "UL9JJ9JJJJ Confirmed.You have received Ksh$amount from $who on 12/11/26 at 12:00 PM  New M-PESA balance is Ksh9,000.00.",
)

class SchemeMatchTest {
    private fun known(p: Placement) = (p as? Placement.Known)?.result ?: throw AssertionError("Expected Known, was $p")

    @Test fun paybillToFundIsSavingsNotSpending() {
        val r = known(Categorizer().place(paybill("BRITAM ASSET MANAGERS", "1,000.00")))
        assertEquals(Category.SAVINGS, r.category)
        assertEquals("britam", r.schemeId)
        assertEquals("Saved into Britam Money Market Fund", r.reason)
        assertFalse(r.category.isSpending)
    }

    @Test fun manyManagersRecognised() {
        val cases = mapOf(
            "SANLAMALLIANZ INVESTMENTS" to "sanlam",
            "SANLAM INVESTMENTS EAST AFRICA" to "sanlam",
            "CIC ASSET MANAGEMENT LIMITED" to "cic",
            "ETICA CAPITAL LIMITED" to "etica",
            "CYTONN ASSET MANAGERS" to "cytonn",
            "LOFTY-CORBAN INVESTMENTS" to "lofty",
            "KUZA ASSET MANAGEMENT" to "kuza",
            "STIMA SACCO SOCIETY" to "sacco",
            "MWALIMU NATIONAL SACCO" to "sacco",
            "CBK DHOWCSD" to "tbills",
        )
        for ((name, id) in cases) {
            val m = SchemeRegistry.match(MerchantKey.normalizeName(name))
            if (id == null) continue
            assertTrue("$name -> $m", m is SchemeRegistry.Match.Strong)
            assertEquals(name, id, (m as SchemeRegistry.Match.Strong).scheme.id)
        }
    }

    @Test fun longestPhraseWins() {
        val m = SchemeRegistry.match("ZIIDI TRADER") as SchemeRegistry.Match.Strong
        assertEquals("shares", m.scheme.id)
        assertEquals("ziidi", (SchemeRegistry.match("ZIIDI MMF") as SchemeRegistry.Match.Strong).scheme.id)
    }

    @Test fun unknownFundStillCountsAsSavings() {
        val r = known(Categorizer().place(paybill("SOMENEW MONEY MARKET FUND", "500.00")))
        assertEquals(Category.SAVINGS, r.category)
        assertNull(r.schemeId)
    }

    @Test fun moneyBackFromFundIsWithdrawalNotIncome() {
        val r = known(Categorizer().place(receivedFrom("CIC ASSET MANAGEMENT", "3,000.00")))
        assertEquals(Category.SAVINGS, r.category)
        assertEquals("Withdrawal from your CIC Money Market Fund", r.reason)
    }

    @Test fun personWithFundLikeSurnameIsAPerson() {
        val r = known(Categorizer().place(receivedFrom("PETER NDOVU 0711000222", "3,000.00")))
        assertEquals(Category.MONEY_IN, r.category)
    }

    @Test fun vagueInvestmentNameAsksFromKsh100() {
        val p = Categorizer().place(till("Chron Investments", "500.00"))
        assertTrue(p is Placement.Ask)
        assertEquals(listOf(Category.SAVINGS, Category.SHOPPING, Category.OTHER), (p as Placement.Ask).choices)
        assertEquals("KSh 500.00 paid to Chron Investments. Was this a saving or investment?", p.prompt)
    }

    @Test fun vagueInvestmentNameUnderKsh100IsTreatedAsAShop() {
        val p = Categorizer().place(till("Chron Investments", "80.00")) as Placement.Ask
        assertEquals(listOf(Category.FOOD, Category.FUEL_TRANSPORT, Category.OTHER), p.choices)
    }
}

class InvestmentTrackerTest {
    private val britam = SchemeRegistry.byId("britam")!!
    private val sacco = SchemeRegistry.byId("sacco")!!
    private val tbills = SchemeRegistry.byId("tbills")!!

    @Test fun beforeAnyCheckInValueIsWhatYouPutIn() {
        val s = InvestmentTracker.summarize(
            britam,
            listOf(
                InvestmentEvent.Contribution(k("5,000"), d(9, 1), EventSource.MPESA),
                InvestmentEvent.Contribution(k("2,000"), d(10, 1), EventSource.MPESA),
                InvestmentEvent.Withdrawal(k("1,000"), d(10, 15), EventSource.MPESA),
            ),
            today = d(11, 1),
        )
        assertEquals(k("6,000"), s.netInvested)
        assertEquals(k("6,000"), s.value)
        assertTrue(s.isEstimate)
        assertNull(s.totalGain)
        assertEquals(d(11, 5), s.nextCheckIn)
    }

    @Test fun monthlyGrowthIsMeasuredNetOfTopUps() {
        // 1 Oct: worth 10,000. Topped up 1,000 on 15 Oct. 1 Nov: worth 11,080 -> earned 80.
        val s = InvestmentTracker.summarize(
            britam,
            listOf(
                InvestmentEvent.ValueReport(k("10,000"), d(10, 1), EventSource.USER),
                InvestmentEvent.Contribution(k("1,000"), d(10, 15), EventSource.MPESA),
                InvestmentEvent.ValueReport(k("11,080"), d(11, 1), EventSource.USER),
            ),
            today = d(11, 1),
        )
        assertEquals(1, s.periods.size)
        assertEquals(k("80"), s.periods[0].gain)
        // 80 / 10,500 average * 365/31 ≈ 8.97% a year
        assertEquals(897, s.periods[0].yearlyRateBps)
        assertEquals(k("11,080"), s.value)
        assertFalse(s.isEstimate)
    }

    @Test fun betweenCheckInsEstimateUsesOwnLastRate() {
        val s = InvestmentTracker.summarize(
            britam,
            listOf(
                InvestmentEvent.ValueReport(k("10,000"), d(10, 1), EventSource.USER),
                InvestmentEvent.ValueReport(k("10,100"), d(10, 31), EventSource.USER),
            ),
            today = d(11, 10),
        )
        assertTrue(s.isEstimate)
        assertTrue(s.value > k("10,100"))
        assertTrue(s.value < k("10,140"))
    }

    @Test fun saccoNeverAssumesGrowthBetweenStatements() {
        val s = InvestmentTracker.summarize(
            sacco,
            listOf(
                InvestmentEvent.ValueReport(k("50,000"), d(1, 1), EventSource.USER),
                InvestmentEvent.ValueReport(k("56,000"), d(6, 1), EventSource.USER),
                InvestmentEvent.Contribution(k("2,000"), d(6, 10), EventSource.MPESA),
            ),
            today = d(11, 1),
        )
        assertEquals(k("58,000"), s.value)
        assertEquals(d(11, 5), s.nextCheckIn) // quarterly from June = Sept, missed, so next 5th
    }

    @Test fun treasuryBillAsksAtMaturity() {
        val s = InvestmentTracker.summarize(
            tbills,
            listOf(InvestmentEvent.Contribution(k("100,000"), d(9, 1), EventSource.MPESA)),
            today = d(11, 1),
            maturity = d(12, 1),
        )
        assertEquals(d(12, 1), s.nextCheckIn)
    }

    @Test fun fundConfirmationDoesNotDoubleCountMpesaPayment() {
        val s = InvestmentTracker.summarize(
            britam,
            listOf(
                InvestmentEvent.Contribution(k("1,000"), d(11, 2), EventSource.MPESA),
                InvestmentEvent.Contribution(k("1,000"), d(11, 3), EventSource.FUND_MESSAGE), // same top-up
                InvestmentEvent.Withdrawal(k("3,000"), d(11, 5), EventSource.FUND_MESSAGE), // paid to bank: only the fund says so
            ),
            today = d(11, 10),
        )
        assertEquals(k("1,000"), s.contributed)
        assertEquals(k("3,000"), s.withdrawn)
    }
}

class FundMessageParserTest {
    @Test fun realRedemptionMessage() {
        val body = "Dear JOHN KIMANI,\nYour redemption request for Kes 3,000.00 has been processed and sent to your account. Thank you for\n" +
            "choosing us.\nFor inquiries, call 0711049000"
        val events = FundMessageParser.parse(body, d(11, 5))
        assertEquals(listOf<InvestmentEvent>(InvestmentEvent.Withdrawal(k("3,000"), d(11, 5), EventSource.FUND_MESSAGE)), events)
    }

    @Test fun topUpAndBalance() {
        val body = "Dear Investor, your top up of KES 1,000.00 has been received and invested. Your balance is KES 12,345.67. Thank you."
        val events = FundMessageParser.parse(body, d(11, 5))
        assertTrue(InvestmentEvent.Contribution(k("1,000"), d(11, 5), EventSource.FUND_MESSAGE) in events)
        assertTrue(InvestmentEvent.ValueReport(k("12,345.67"), d(11, 5), EventSource.FUND_MESSAGE) in events)
    }

    @Test fun statementBalanceOnly() {
        val body = "Your portfolio value as at 31/10/2026 is KES 25,400.10. Interest earned this month KES 190.20."
        val events = FundMessageParser.parse(body, d(11, 1))
        assertEquals(listOf<InvestmentEvent>(InvestmentEvent.ValueReport(k("25,400.10"), d(11, 1), EventSource.FUND_MESSAGE)), events)
    }

    @Test fun unrelatedMessageGivesNothing() {
        assertTrue(FundMessageParser.parse("Your OTP is 448812. Do not share it.", d(11, 1)).isEmpty())
    }
}
