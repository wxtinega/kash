package com.kash.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

private val NOW: Instant = Instant.parse("2026-11-11T12:00:00Z")

private fun pipeline() = SmsPipeline()
private fun mpesa(body: String) = RawSms("MPESA", body, NOW)
private fun ksh(s: String) = Money.parseNumber(s)!!

private fun parsed(outcome: ParseOutcome): Transaction {
    assertTrue("Expected Parsed but was $outcome", outcome is ParseOutcome.Parsed)
    return (outcome as ParseOutcome.Parsed).tx
}

class MoneyTest {
    @Test fun parsesWithAndWithoutCommasAndDecimals() {
        assertEquals(150_000L, ksh("1,500.00").cents)
        assertEquals(25_000L, ksh("250").cents)
        assertEquals(50L, ksh("0.5").cents)
        assertEquals(49_950_000L, ksh("499,500.00").cents)
    }

    @Test fun rejectsMalformed() {
        assertNull(Money.parseNumber("1,50.00"))
        assertNull(Money.parseNumber("12.345"))
        assertNull(Money.parseNumber("abc"))
        assertNull(Money.parseNumber("99999999999999"))
    }

    @Test fun formatsForDisplay() {
        assertEquals("KSh 1,500.00", ksh("1,500").format())
        assertEquals("-KSh 7.00", (-ksh("7")).format())
    }

    @Test fun arithmeticIsExact() {
        var total = Money.ZERO
        repeat(1000) { total += ksh("0.10") }
        assertEquals(ksh("100"), total)
    }
}

class MpesaParserTest {
    @Test fun received() {
        val tx = parsed(pipeline().process(mpesa(Samples.RECEIVED)))
        assertEquals(TxType.RECEIVED, tx.type)
        assertEquals("TKA1B2C3D4", tx.code)
        assertEquals(ksh("1,500.00"), tx.amount)
        assertEquals("JOHN KAMAU", tx.counterparty)
        assertEquals("0712345678", tx.counterpartyPhone)
        assertEquals(ksh("3,250.50"), tx.balanceAfter)
        assertEquals(LocalDateTime.of(2026, 11, 10, 14, 15), tx.occurredAt)
        assertEquals(Confidence.HIGH, tx.confidence)
    }

    @Test fun sentWithFee() {
        val tx = parsed(pipeline().process(mpesa(Samples.SENT)))
        assertEquals(TxType.SENT, tx.type)
        assertEquals(ksh("500"), tx.amount)
        assertEquals(ksh("7"), tx.fee)
        assertEquals("JANE WANJIKU", tx.counterparty)
    }

    @Test fun paybillCapturesAccount() {
        val tx = parsed(pipeline().process(mpesa(Samples.PAYBILL)))
        assertEquals(TxType.PAYBILL, tx.type)
        assertEquals("KPLC PREPAID", tx.counterparty)
        assertEquals("37123456789", tx.accountNumber)
    }

    @Test fun till() {
        val tx = parsed(pipeline().process(mpesa(Samples.TILL)))
        assertEquals(TxType.TILL, tx.type)
        assertEquals("MAMA OLIECH CAFE", tx.counterparty)
        assertEquals(ksh("250"), tx.amount)
    }

    @Test fun withdraw() {
        val tx = parsed(pipeline().process(mpesa(Samples.WITHDRAW)))
        assertEquals(TxType.WITHDRAW, tx.type)
        assertEquals(ksh("29"), tx.fee)
        assertEquals(LocalDateTime.of(2026, 11, 10, 19, 0), tx.occurredAt)
    }

    @Test fun airtimeDepositMshwari() {
        assertEquals(TxType.AIRTIME, parsed(pipeline().process(mpesa(Samples.AIRTIME))).type)
        assertEquals(TxType.DEPOSIT, parsed(pipeline().process(mpesa(Samples.DEPOSIT))).type)
        val save = parsed(pipeline().process(mpesa(Samples.MSHWARI_SAVE)))
        assertEquals(TxType.MSHWARI_SAVE, save.type)
        assertEquals(ksh("1,864.50"), save.balanceAfter) // M-Pesa balance, not M-Shwari balance
    }

    @Test fun fuliza() {
        val loan = parsed(pipeline().process(mpesa(Samples.FULIZA_LOAN)))
        assertEquals(TxType.FULIZA_LOAN, loan.type)
        assertEquals(ksh("700"), loan.amount)
        assertEquals(ksh("7"), loan.fee)
        assertEquals(ksh("707"), loan.fulizaOutstanding)
        val repay = parsed(pipeline().process(mpesa(Samples.FULIZA_REPAY)))
        assertEquals(TxType.FULIZA_REPAY, repay.type)
        assertEquals(ksh("1,293"), repay.balanceAfter)
    }

    @Test fun reversalReferencesOriginal() {
        val tx = parsed(pipeline().process(mpesa(Samples.REVERSAL)))
        assertEquals(TxType.REVERSAL, tx.type)
        assertEquals("TKB2C3D4E5", tx.reversesCode)
        assertEquals(ksh("3,250.50"), tx.balanceAfter)
    }

    @Test fun failedAndPromosAreIgnored() {
        assertEquals(ParseOutcome.Ignored(IgnoreReason.FAILED_TRANSACTION), pipeline().process(mpesa(Samples.FAILED)))
        assertEquals(ParseOutcome.Ignored(IgnoreReason.NOT_A_TRANSACTION), pipeline().process(mpesa(Samples.PROMO)))
    }

    @Test fun unknownFormatGoesToReview() {
        val body = "TKX1X2X3X4 Confirmed. Ksh300.00 zapped to SOMEONE via a brand new service on 10/11/26 at 1:00 PM."
        val out = pipeline().process(mpesa(body))
        assertTrue(out is ParseOutcome.NeedsReview)
    }

    @Test fun missingBalanceGoesToReviewNotParsed() {
        val body = "TKX1X2X3X5 Confirmed. Ksh500.00 sent to JANE WANJIKU 0722123456 on 10/11/26 at 3:01 PM."
        val out = pipeline().process(mpesa(body))
        assertTrue(out is ParseOutcome.NeedsReview)
        assertEquals(Confidence.LOW, (out as ParseOutcome.NeedsReview).partial?.confidence)
    }

    @Test fun duplicatesAreDropped() {
        val p = pipeline()
        parsed(p.process(mpesa(Samples.SENT)))
        assertEquals(ParseOutcome.Duplicate("TKB2C3D4E5"), p.process(mpesa(Samples.SENT)))
    }

    @Test fun senderSpellingVariantsAreTrusted() {
        parsed(pipeline().process(RawSms("M-PESA", Samples.RECEIVED, NOW)))
        parsed(pipeline().process(RawSms(" mpesa ", Samples.RECEIVED, NOW)))
    }
}

class HostileInputTest {
    @Test fun fullWidthDigitsAreNormalised() {
        val tricky = Samples.SENT.replace("500.00 sent", "５００.００ sent")
        assertEquals(ksh("500"), parsed(pipeline().process(mpesa(tricky))).amount)
    }

    @Test fun oversizedAndEmptyAreRejected() {
        assertEquals(ParseOutcome.Ignored(IgnoreReason.MALFORMED), pipeline().process(mpesa("x".repeat(5000))))
        assertEquals(ParseOutcome.Ignored(IgnoreReason.MALFORMED), pipeline().process(mpesa("   ")))
    }

    @Test fun namesAreMadeDisplaySafe() {
        val body = Samples.SENT.replace("JANE WANJIKU", "<script>alert(1)</script>JANE")
        val tx = parsed(pipeline().process(mpesa(body)))
        assertTrue(tx.counterparty!!.none { it == '<' || it == '>' || it == '"' })
    }

    @Test fun absurdAmountsAreRejected() {
        val body = Samples.SENT.replace("Ksh500.00", "Ksh99,999,999,999.00")
        assertTrue(pipeline().process(mpesa(body)) !is ParseOutcome.Parsed)
    }
}

class ScamTest {
    @Test fun fakeConfirmationFromPhoneNumberIsFlagged() {
        val out = pipeline().process(RawSms("0733111222", Samples.SCAM_FAKE_CONFIRMATION, NOW))
        assertTrue("was $out", out is ParseOutcome.ScamAlert)
        assertTrue(ScamSignal.IMITATES_PROVIDER in (out as ParseOutcome.ScamAlert).signals)
    }

    @Test fun genuineWordingFromPhoneNumberIsNeverRecordedAsMoney() {
        // Even a perfect copy of a real message is not trusted when it comes from a phone number.
        val out = pipeline().process(RawSms("+254733111222", Samples.RECEIVED, NOW))
        assertTrue(out !is ParseOutcome.Parsed)
    }

    @Test fun swahiliWrongTransferStoryIsFlagged() {
        val out = pipeline().process(RawSms("0733111222", Samples.SCAM_WRONG_TRANSFER, NOW))
        // Story alone (35) is below threshold; it's ignored but never recorded.
        assertTrue(out is ParseOutcome.Ignored || out is ParseOutcome.ScamAlert)
        assertTrue(ScamSignal.WRONG_TRANSFER_STORY in ScamDetector.signals(Samples.SCAM_WRONG_TRANSFER))
    }

    @Test fun prizeAndPinScamIsFlagged() {
        val out = pipeline().process(RawSms("0711999888", Samples.SCAM_PRIZE, NOW))
        assertTrue(out is ParseOutcome.ScamAlert)
        val signals = (out as ParseOutcome.ScamAlert).signals
        assertTrue(ScamSignal.ASKS_FOR_PIN in signals)
        assertTrue(ScamSignal.PRIZE_OR_URGENCY in signals)
    }

    @Test fun ordinaryChatIsQuietlyIgnored() {
        assertEquals(
            ParseOutcome.Ignored(IgnoreReason.UNTRUSTED_SENDER),
            pipeline().process(RawSms("0722000111", Samples.FRIEND_CHAT, NOW)),
        )
    }
}

class BankParserTest {
    @Test fun creditWithBalance() {
        val body = "Dear Customer, your account ****1234 has been credited with KES 5,000.00 on 10-11-2026. " +
            "Ref: FT26314ABCDE. Available balance: KES 12,345.60."
        val tx = parsed(pipeline().process(RawSms("EQUITYBANK", body, NOW)))
        assertEquals(TxType.BANK_CREDIT, tx.type)
        assertEquals(ksh("5,000"), tx.amount)
        assertEquals(ksh("12,345.60"), tx.balanceAfter)
        assertEquals("EQUITY-FT26314ABCDE", tx.code)
    }

    @Test fun debitWithoutReferenceStillDeduplicates() {
        val body = "Your account ****1234 has been debited KES 1,200.00 for a purchase at NAIVAS. Bal KES 11,145.60"
        val p = pipeline()
        val tx = parsed(p.process(RawSms("KCB", body, NOW)))
        assertEquals(TxType.BANK_DEBIT, tx.type)
        assertEquals(ksh("1,200"), tx.amount)
        assertTrue(p.process(RawSms("KCB", body, NOW)) is ParseOutcome.Duplicate)
    }

    @Test fun otpIsIgnored() {
        val out = pipeline().process(RawSms("NCBA", "Your one-time PIN is 482913. Do not share it with anyone.", NOW))
        assertEquals(ParseOutcome.Ignored(IgnoreReason.NOT_A_TRANSACTION), out)
    }

    @Test fun ambiguousDirectionGoesToReview() {
        val body = "Transaction of KES 800.00 on your account ****1234. Avail bal KES 3,000.00"
        assertTrue(pipeline().process(RawSms("COOPBANK", body, NOW)) is ParseOutcome.NeedsReview)
    }
}

class BalanceChainTest {
    private fun all(vararg bodies: String): List<Transaction> {
        val p = pipeline()
        return bodies.map { parsed(p.process(mpesa(it))) }
    }

    @Test fun consistentDayHasNoBreaks() {
        val txs = all(Samples.RECEIVED, Samples.SENT, Samples.PAYBILL, Samples.TILL, Samples.WITHDRAW, Samples.AIRTIME)
        assertEquals(emptyList<BalanceChain.Break>(), BalanceChain.check(txs))
    }

    @Test fun missingMessageShowsAsBreak() {
        val txs = all(Samples.RECEIVED, Samples.SENT, /* PAYBILL missing */ Samples.TILL)
        val breaks = BalanceChain.check(txs)
        assertEquals(1, breaks.size)
        assertEquals("TKD4E5F6G7", breaks[0].tx.code)
        assertEquals(-ksh("1,000"), breaks[0].difference)
    }

    @Test fun fulizaCreditExplainsGap() {
        val p = pipeline()
        val before = parsed(p.process(mpesa(
            "TKP1P2P3P4 Confirmed. Ksh100.00 sent to JANE WANJIKU 0722123456 on 12/11/26 at 1:00 PM. " +
                "New M-PESA balance is Ksh300.00. Transaction cost, Ksh0.00.",
        )))
        val pay = parsed(p.process(mpesa(
            "TKP2P3P4P5 Confirmed. Ksh1,000.00 paid to NAIVAS KAHAWA. on 12/11/26 at 2:00 PM.New M-PESA balance is Ksh0.00. " +
                "Transaction cost, Ksh0.00.",
        )))
        val loan = parsed(p.process(mpesa(Samples.FULIZA_LOAN))).copy(occurredAt = pay.occurredAt)
        assertEquals(emptyList<BalanceChain.Break>(), BalanceChain.check(listOf(before, pay, loan)))
    }
}
