package com.kash.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

/** Real Airtel Money and Citibank messages (Oct–Nov 2026); names and numbers changed. */
class RealAirtelCitiTest {
    private val now = Instant.parse("2026-11-11T08:00:00Z")
    private fun ksh(s: String) = Money.parseNumber(s)!!
    private fun run(p: SmsPipeline, sender: String, body: String) = p.process(RawSms(sender, body, now))
    private fun airtel(body: String, p: SmsPipeline = SmsPipeline()) =
        (run(p, "AirtelMoney", body) as? ParseOutcome.Parsed)?.tx ?: throw AssertionError("Not parsed: ${run(SmsPipeline(), "AirtelMoney", body)}")

    companion object {
        const val SENT = "33T16RW138U. Ksh 90 sent to daniel wafula 254757000111 on 30/10/26 at 02:11 PM. Fee: Ksh 0. Bal: Ksh 0.0. MPESA ID: UJU6YBTOQQ"
        const val BUNDLE_29 = "56705560258 Confirmed. Bundle purchase successful of Ksh 15 via Airtel Networks Kenya Ltd on 29/10/26 at 07:42 PM. Bal: Ksh 90.0."
        const val RECEIVED_27 = "TID:G3SW6PY4RFG. Received Ksh 100 from Rodney Mwangi Kamau 254790000222 on 27/10/26 04:20 PM. Bal:Ksh 105.0 Sender TID:UJRFPBLVVF."
        const val SENT_27 = "N3SW6EWVKVP. Ksh 50 sent to George Otieno 254110000333 on 27/10/26 at 04:11 PM. Fee: Ksh 0. Bal: Ksh 5.0. MPESA ID: UJRE7BTNIU"
        const val LIPA = "73STEWM8DAH. Ksh 60 paid to Lipa Na Mpesa via Airtel Money on 25/10/26 at 04:04 PM. Fee: Ksh 0. Bal: Ksh 15.0. MPESA ID: UJPS017D58"
        const val CITI = "You have received KES 5000 from STRATHMORE UNIVERSITY with M-Pesa Ref No UKBDR26KF6. Details: DYLSTDSTP1026"
    }

    @Test fun sentToMpesaNumber() {
        val t = airtel(SENT)
        assertEquals(TxType.SENT, t.type)
        assertEquals(ksh("90"), t.amount)
        assertEquals(Money.ZERO, t.fee)
        assertEquals(Money.ZERO, t.balanceAfter)
        assertEquals("daniel wafula", t.counterparty)
        assertEquals("254757000111", t.counterpartyPhone)
        assertEquals("AIRTEL-33T16RW138U", t.code)
        assertEquals("UJU6YBTOQQ", t.linkedCode)
        assertEquals(LocalDateTime.of(2026, 10, 30, 14, 11), t.occurredAt)
    }

    @Test fun bundlePurchaseIsABill() {
        val t = airtel(BUNDLE_29)
        assertEquals(TxType.PAYBILL, t.type)
        assertEquals("Airtel data bundles", t.counterparty)
        assertEquals(ksh("15"), t.amount)
        assertEquals(ksh("90"), t.balanceAfter)
        assertEquals("AIRTEL-56705560258", t.code)
    }

    @Test fun receivedWithoutAtBeforeTime() {
        val t = airtel(RECEIVED_27)
        assertEquals(TxType.RECEIVED, t.type)
        assertEquals("Rodney Mwangi Kamau", t.counterparty)
        assertEquals(ksh("105"), t.balanceAfter)
        assertEquals("AIRTEL-G3SW6PY4RFG", t.code)
        assertEquals("UJRFPBLVVF", t.linkedCode)
        assertEquals(LocalDateTime.of(2026, 10, 27, 16, 20), t.occurredAt)
    }

    @Test fun lipaNaMpesaWithNoShopNamed() {
        val t = airtel(LIPA)
        assertEquals(TxType.TILL, t.type)
        assertEquals(AirtelMoneyParser.UNNAMED_LIPA_NA_MPESA, t.counterparty)
        assertEquals(ksh("15"), t.balanceAfter)
    }

    @Test fun airtelBalanceChainAddsUp() {
        // 27 Oct: sent 50 -> 5; received 100 -> 105. 29 Oct: bundle 15 -> 90. 30 Oct: sent 90 -> 0.
        val p = SmsPipeline()
        val txs = listOf(SENT_27, RECEIVED_27, BUNDLE_29, SENT).map { airtel(it, p) }
        assertEquals(emptyList<BalanceChain.Break>(), BalanceChain.check(txs))
    }

    @Test fun citiNoticeOfMpesaPaymentIsNotCountedTwice() {
        assertEquals(SenderClass.Trusted(Provider.CITI), SenderPolicy.default().classify("Citibank"))
        assertEquals(ParseOutcome.Ignored(IgnoreReason.NOT_A_TRANSACTION), run(SmsPipeline(), "Citibank", CITI))
    }
}
