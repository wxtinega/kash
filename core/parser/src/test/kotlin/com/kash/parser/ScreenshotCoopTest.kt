package com.kash.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** From a real Co-op thread (11 Nov 2026); names and numbers changed. */
class ScreenshotCoopTest {
    private fun at(s: String) = Instant.parse(s)
    private val receipt = "Dear PAUL JAMES MOKAYA, you have sent Ksh. 1020.0 to JOHN KIMANI MOKAYA 01103******001\n on 11/11/2026 at 20:28:59. MPESA Ref. UKBDR28PWY. Pay bills on MCoopCash App or Dial *667#."
    private val deposit = "Ref: AC566EF25626. Dear JOHN, your M-PESA to Account Deposit of KES 1020 to account 01103999888001 was successful. M-PESA Ref: UKBDR28PWY."
    private val fundDeposit = "Dear JOHN KIMANI, We confirm a deposit of 700.00 to your CL42326. Tx ID: CB1177155. For enquiries call 0711049000"
    private val redemption = "Dear JOHN KIMANI,\nYour redemption request for Kes 3,000.00 has been processed and sent to your account. Thank you for\nchoosing us.\nFor inquiries, call 0711049000"
    private val earlierDeposit = "Ref: AC54CB831326. Dear JOHN, your M-PESA to Account Deposit of KES 320 to account 01103999888001 was successful. M-PESA Ref: UK7DR1OIW6."

    @Test fun oneDepositTwoMessagesCountsOnce() {
        val p = SmsPipeline()
        p.process(RawSms("Co-opBank", earlierDeposit, at("2026-11-07T08:00:00Z"))) // teaches the account number
        val a = p.process(RawSms("Co-opBank", receipt, at("2026-11-11T17:28:59Z")))
        val b = p.process(RawSms("Co-opBank", deposit, at("2026-11-11T17:29:10Z")))
        val counted = listOf(a, b).count { it is ParseOutcome.Parsed }
        assertEquals(1, counted)
        assertTrue(listOf(a, b).any { it is ParseOutcome.Duplicate })
    }

    @Test fun samePairInOtherOrderAlsoCountsOnce() {
        val p = SmsPipeline()
        val a = p.process(RawSms("Co-opBank", deposit, at("2026-11-11T17:29:10Z")))
        val b = p.process(RawSms("Co-opBank", receipt, at("2026-11-11T17:29:30Z")))
        assertTrue(a is ParseOutcome.Parsed)
        assertTrue("was $b", b is ParseOutcome.Duplicate)
    }

    @Test fun fundDepositConfirmationIsSavingsInfoNotMoney() {
        val t = (SmsPipeline().process(RawSms("Co-opBank", fundDeposit, at("2026-11-11T17:32:00Z"))) as ParseOutcome.Parsed).tx
        assertEquals(TxType.FUND_DEPOSIT, t.type)
        assertEquals(Flow.NONE, t.type.flow)
        assertEquals(Money.parseNumber("700"), t.amount)
        assertEquals("CL42326", t.accountNumber)
        assertEquals("COOP-CB1177155", t.code)
        assertEquals("Co-op Money Market Fund", t.counterparty)
    }

    @Test fun fundRedemption() {
        val t = (SmsPipeline().process(RawSms("Co-opBank", redemption, at("2026-11-05T08:00:00Z"))) as ParseOutcome.Parsed).tx
        assertEquals(TxType.FUND_REDEMPTION, t.type)
        assertEquals(Money.parseNumber("3,000"), t.amount)
    }

    @Test fun unknownCoopSenderIdIsConfirmedNotIgnored() {
        for (id in listOf("CO-OP", "COOPBANKKE", "Co-opBankKE")) {
            val o = SmsPipeline().process(RawSms(id, deposit, at("2026-11-11T17:29:10Z")))
            assertTrue("$id -> $o", o is ParseOutcome.ConfirmSender)
            assertEquals(Provider.COOP, (o as ParseOutcome.ConfirmSender).provider)
        }
    }

    @Test fun confirmedSenderIsTrustedAfterwards() {
        val p = SmsPipeline(senders = SenderPolicy.withConfirmed(mapOf("CO-OP" to Provider.COOP)))
        assertTrue(p.process(RawSms("CO-OP", deposit, at("2026-11-11T17:29:10Z"))) is ParseOutcome.Parsed)
    }

    @Test fun lookalikeWithScamContentIsStillAScam() {
        val o = SmsPipeline().process(RawSms("COOPBANKKE", "Your account is blocked. Send your PIN to 0711999888 to unlock immediately.", at("2026-11-11T17:00:00Z")))
        assertTrue("was $o", o is ParseOutcome.ScamAlert)
    }
}
