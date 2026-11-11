package com.kash.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

/** Tester feedback, round 1: Airtel Money, Citibank, new formats, same-minute ordering. */
class FeedbackRound1Test {
    private val now = Instant.parse("2026-11-11T08:00:00Z")
    private fun ksh(s: String) = Money.parseNumber(s)!!
    private fun run(sender: String, body: String, at: Instant = now) = SmsPipeline().process(RawSms(sender, body, at))
    private fun parsed(o: ParseOutcome) = (o as? ParseOutcome.Parsed)?.tx ?: throw AssertionError("Not parsed: $o")

    @Test fun airtelPromoIgnoredAndFakeAirtelFromPhoneIsNotMoney() {
        assertEquals(ParseOutcome.Ignored(IgnoreReason.NOT_A_TRANSACTION), run("AirtelMoney", "Dial *334# to send money free to any Airtel line this weekend!"))
        assertTrue(run("0733000111", "Airtel Money: You received KES 5,000.00 from MARY (0723456789). Your new balance is KES 5,100.00. Transaction ID: AIR111.") !is ParseOutcome.Parsed)
    }

    // ---- Citibank: sender trusted; generic bank reader until real samples arrive ----
    @Test fun citibankIsTrusted() {
        assertEquals(SenderClass.Trusted(Provider.CITI), SenderPolicy.default().classify("CITIBANK"))
        val t = parsed(run("Citibank", "Your account xxxx1234 has been debited with KES 2,500.00. Ref: CB12345678. Available balance: KES 40,100.00"))
        assertEquals(Provider.CITI, t.provider)
        assertEquals(TxType.BANK_DEBIT, t.type)
    }

    // ---- New M-Pesa formats: money is still accounted for ----
    @Test fun unknownMpesaFormatStillCapturesAmountCostAndBalance() {
        val o = run("MPESA", "UL7AB12CDE Confirmed. Ksh50.00 used to buy SAFARICOM DATA BUNDLES on 11/11/26 at 9:00 AM. New M-PESA balance is Ksh950.00. Transaction cost, Ksh0.00.")
        assertTrue(o is ParseOutcome.NeedsReview)
        val t = (o as ParseOutcome.NeedsReview).partial!!
        assertEquals(TxType.OTHER_OUT, t.type)
        assertEquals(ksh("50"), t.amount)
        assertEquals(ksh("950"), t.balanceAfter)
    }

    @Test fun newMaskedNumberStyle() {
        val t = parsed(run("MPESA", "UL8XY12ABC Confirmed.You have received Ksh700.00 from GRACE AKINYI 0722*000** on 11/11/26 at 10:00 AM  New M-PESA balance is Ksh1,650.00."))
        assertEquals("GRACE AKINYI", t.counterparty)
        assertEquals("0722*000**", t.counterpartyPhone)
    }

    // ---- Two payments in the same minute ----
    @Test fun sameMinuteOrderedByReceiveTimeSoNoFalseGap() {
        val p = SmsPipeline()
        val first = parsed(p.process(RawSms("MPESA", "UL9ZZ99ZZ1 Confirmed. Ksh100.00 paid to KIOSK. on 11/11/26 at 1:00 PM.New M-PESA balance is Ksh900.00. Transaction cost, Ksh0.00.", Instant.parse("2026-11-11T10:00:05Z"))))
        val second = parsed(p.process(RawSms("MPESA", "UL9AA11AA2 Confirmed. Ksh200.00 paid to KIOSK. on 11/11/26 at 1:00 PM.New M-PESA balance is Ksh700.00. Transaction cost, Ksh0.00.", Instant.parse("2026-11-11T10:00:40Z"))))
        val start = parsed(p.process(RawSms("MPESA", "UL9BB22BB3 Confirmed.You have received Ksh1,000.00 from ANN 0711*222** on 11/11/26 at 12:00 PM  New M-PESA balance is Ksh1,000.00.", Instant.parse("2026-11-11T09:00:00Z"))))
        // Codes sort the "wrong" way round (UL9AA… < UL9ZZ…); receive time must win.
        assertEquals(emptyList<BalanceChain.Break>(), BalanceChain.check(listOf(second, first, start)))
    }
}
