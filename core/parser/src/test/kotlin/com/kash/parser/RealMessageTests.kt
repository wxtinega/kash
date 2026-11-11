package com.kash.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class RealMpesaTest {
    private val now = Instant.parse("2026-11-11T08:00:00Z")
    private fun run(p: SmsPipeline, body: String) = p.process(RawSms("MPESA", body, now))
    private fun tx(body: String) = (run(SmsPipeline(), body) as? ParseOutcome.Parsed)?.tx
        ?: throw AssertionError("Not parsed: ${run(SmsPipeline(), body)}")
    private fun ksh(s: String) = Money.parseNumber(s)!!

    @Test fun sentWithDoubleSpacesAndMixedCaseName() {
        val t = tx(RealSamples.SENT)
        assertEquals(TxType.SENT, t.type)
        assertEquals("ABEL otieno", t.counterparty)
        assertEquals("0727000111", t.counterpartyPhone)
        assertEquals(ksh("416.74"), t.balanceAfter)
        assertEquals(LocalDateTime.of(2026, 11, 10, 21, 12), t.occurredAt)
    }

    @Test fun receivedWithMaskedPhone() {
        val t = tx(RealSamples.RECEIVED_MASKED)
        assertEquals(TxType.RECEIVED, t.type)
        assertEquals("PETER MWANGI", t.counterparty)
        assertEquals("0720***080", t.counterpartyPhone)
    }

    @Test fun tillAndPaybillWithApostropheAccount() {
        assertEquals("Chron Investments", tx(RealSamples.TILL).counterparty)
        val p = tx(RealSamples.PAYBILL)
        assertEquals(TxType.PAYBILL, p.type)
        assertEquals("KINGS CHOICE WINE LIMITED", p.counterparty)
        assertEquals("king's", p.accountNumber)
        assertEquals(ksh("5"), p.fee)
    }

    @Test fun fulizaRepayWithCodeOnOwnLineAndNoCurrencyOnBalance() {
        val t = tx(RealSamples.FULIZA_REPAY)
        assertEquals("UJUDR0N233", t.code)
        assertEquals(TxType.FULIZA_REPAY, t.type)
        assertEquals(ksh("300.66"), t.amount)
        assertEquals(ksh("699.34"), t.balanceAfter)
    }

    @Test fun fulizaLoanWithCentsFee() {
        val t = tx(RealSamples.FULIZA_LOAN)
        assertEquals(ksh("20"), t.amount)
        assertEquals(ksh("0.20"), t.fee)
        assertEquals(ksh("300.23"), t.fulizaOutstanding)
    }

    @Test fun failedIsIgnored() {
        assertEquals(ParseOutcome.Ignored(IgnoreReason.FAILED_TRANSACTION), run(SmsPipeline(), RealSamples.FAILED))
    }

    @Test fun duplicatedRepayMessageCountedOnce() {
        val p = SmsPipeline()
        assertTrue(run(p, RealSamples.FULIZA_REPAY) is ParseOutcome.Parsed)
        assertEquals(ParseOutcome.Duplicate("UJUDR0N233"), run(p, RealSamples.FULIZA_REPAY))
    }

    @Test fun balanceChainOnRealDataFindsTheMissingKsh40() {
        // 8 Nov: 56.74 -> received 500 -> 556.74 (adds up)
        // 10 Nov: 556.74 -> sent 100 -> 416.74, but 456.74 was expected: KSh 40 left in a message we don't have.
        val p = SmsPipeline()
        val txs = listOf(RealSamples.SENT_SMALL, RealSamples.RECEIVED_MASKED, RealSamples.SENT)
            .map { (run(p, it) as ParseOutcome.Parsed).tx }
        val breaks = BalanceChain.check(txs)
        assertEquals(1, breaks.size)
        assertEquals("UKADR23ZKR", breaks[0].tx.code)
        assertEquals(-ksh("40"), breaks[0].difference)
    }
}

class RealCoopTest {
    private val now = Instant.parse("2026-11-11T08:00:00Z")
    private fun run(p: SmsPipeline, body: String) = p.process(RawSms("Co-opBank", body, now))
    private fun parsed(o: ParseOutcome) = (o as? ParseOutcome.Parsed)?.tx ?: throw AssertionError("Not parsed: $o")
    private fun ksh(s: String) = Money.parseNumber(s)!!

    @Test fun senderNameIsTrusted() {
        assertEquals(SenderClass.Trusted(Provider.COOP), SenderPolicy.default().classify("Co-opBank"))
    }

    @Test fun transferToSomeonesMpesa() {
        val t = parsed(run(SmsPipeline(), RealSamples.COOP_TO_MPESA_OTHER))
        assertEquals(TxType.BANK_DEBIT, t.type)
        assertEquals(ksh("300"), t.amount)
        assertEquals("LINDA ACHIENG", t.counterparty)
        assertEquals("254722000333", t.counterpartyPhone)
        assertEquals("COOP-AC54CC26C326", t.code)
        assertEquals("UK75E1HSWW", t.linkedCode)
        assertEquals(LocalDateTime.of(2026, 11, 7, 16, 38), t.occurredAt)
    }

    @Test fun depositFromOwnMpesaIsLinkedForTransferDetection() {
        val t = parsed(run(SmsPipeline(), RealSamples.COOP_FROM_MPESA))
        assertEquals(TxType.BANK_CREDIT, t.type)
        assertEquals(ksh("320"), t.amount)
        assertEquals("From your M-Pesa", t.counterparty)
        assertEquals("UK7DR1OIW6", t.linkedCode)
        assertEquals("COOP-AC54CB831326", t.code)
    }

    @Test fun duplicatedDepositCountedOnce() {
        val p = SmsPipeline()
        parsed(run(p, RealSamples.COOP_FROM_MPESA))
        assertEquals(ParseOutcome.Duplicate("COOP-AC54CB831326"), run(p, RealSamples.COOP_FROM_MPESA))
    }

    @Test fun transferToOwnMpesa() {
        val t = parsed(run(SmsPipeline(), RealSamples.COOP_TO_OWN_MPESA))
        assertEquals(TxType.BANK_DEBIT, t.type)
        assertEquals(ksh("50"), t.amount)
        assertEquals("To your M-Pesa", t.counterparty)
        assertEquals("UJODRBT8Q9", t.linkedCode)
    }

    @Test fun foreignCardPurchaseKeepsUsdAndBalanceForReview() {
        val o = run(SmsPipeline(), RealSamples.COOP_CARD_USD)
        assertTrue("was $o", o is ParseOutcome.NeedsReview)
        val t = (o as ParseOutcome.NeedsReview).partial!!
        assertEquals("USD 23.20", t.foreignAmount)
        assertEquals("ANTHROPIC* CLAUDE SUB", t.counterparty)
        assertEquals(ksh("344.04"), t.balanceAfter)
        assertEquals(LocalDateTime.of(2026, 10, 20, 12, 46, 42), t.occurredAt)
    }

    @Test fun paybillRequestIsPending() {
        val t = parsed(run(SmsPipeline(), RealSamples.COOP_PAYBILL_PENDING))
        assertEquals(TxType.BANK_DEBIT, t.type)
        assertEquals(ksh("1600"), t.amount)
        assertEquals("Paybill 247247", t.counterparty)
        assertEquals("0350000111222", t.accountNumber)
        assertTrue(t.pending)
        assertEquals("COOP-AC41F88BE826", t.code)
    }

    @Test fun paymentIntoUnknownAccountAsksOnce() {
        val o = run(SmsPipeline(), RealSamples.COOP_PAID_IN_BY_OTHER)
        assertTrue("was $o", o is ParseOutcome.NeedsReview)
        assertEquals("Is account 01103****001 yours?", (o as ParseOutcome.NeedsReview).why)
    }

    @Test fun paymentIntoLearnedOwnAccountIsACredit() {
        val p = SmsPipeline()
        parsed(run(p, RealSamples.COOP_FROM_MPESA)) // teaches the app account 01103999888001
        val t = parsed(run(p, RealSamples.COOP_PAID_IN_BY_OTHER))
        assertEquals(TxType.BANK_CREDIT, t.type)
        assertEquals(ksh("4000"), t.amount)
        assertEquals("PAUL JAMES MOKAYA", t.counterparty)
        assertEquals("UI2DR5F56T", t.linkedCode)
        assertEquals(LocalDateTime.of(2026, 2, 9, 16, 31, 49), t.occurredAt)
    }

    @Test fun paymentIntoSomeoneElsesAccountIsIgnored() {
        val p = SmsPipeline(banks = BankParser(ownAccounts = OwnAccounts.of(listOf("01200555666002"))))
        assertEquals(ParseOutcome.Ignored(IgnoreReason.NOT_A_TRANSACTION), run(p, RealSamples.COOP_PAID_IN_BY_OTHER))
    }

    @Test fun securityBannerDoesNotTriggerAnything() {
        // "NEVER share your PIN" must not confuse trusted bank messages.
        val t = parsed(run(SmsPipeline(), RealSamples.COOP_TO_MPESA_OTHER))
        assertNull(t.foreignAmount)
    }

    @Test fun maskMatching() {
        assertTrue(OwnAccounts.maskMatches("01103****001", "01103999888001"))
        assertTrue(!OwnAccounts.maskMatches("01103****002", "01103999888001"))
    }
}
