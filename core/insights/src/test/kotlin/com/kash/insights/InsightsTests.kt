package com.kash.insights

import com.kash.parser.Money
import com.kash.parser.ParseOutcome
import com.kash.parser.Provider
import com.kash.parser.RawSms
import com.kash.parser.ScamSignal
import com.kash.parser.SmsPipeline
import com.kash.parser.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

private val NOW = Instant.parse("2026-11-11T08:00:00Z")
private fun ksh(s: String) = Money.parseNumber(s)!!
private fun mpesa(body: String): Transaction = (SmsPipeline().process(RawSms("MPESA", body, NOW)) as ParseOutcome.Parsed).tx
private fun coop(p: SmsPipeline, body: String): Transaction = (p.process(RawSms("Co-opBank", body, NOW)) as ParseOutcome.Parsed).tx

private const val TILL_CHRON =
    "UK7DR1NBQN Confirmed. Ksh80.00 paid to Chron Investments. on 7/11/26 at 12:09 PM.New M-PESA balance is Ksh558.74. " +
        "Transaction cost, Ksh0.00. Amount you can transact within the day is 499,920.00."
private const val TILL_CHRON_AGAIN =
    "UL1AA1AAAA Confirmed. Ksh120.00 paid to Chron Investments. on 12/11/26 at 1:00 PM.New M-PESA balance is Ksh438.74. " +
        "Transaction cost, Ksh0.00."
private const val PAYBILL_WINE =
    "UJVDR0TTGL Confirmed. Ksh300.00 sent to KINGS CHOICE WINE LIMITED for account king's on 31/10/26 at 10:12 PM " +
        "New M-PESA balance is Ksh662.34. Transaction cost, Ksh5.00."
private const val PAYBILL_KPLC =
    "UL2BB2BBBB Confirmed. Ksh500.00 sent to KPLC PREPAID for account 37123456789 on 12/11/26 at 4:00 PM " +
        "New M-PESA balance is Ksh900.00. Transaction cost, Ksh0.00."
private const val SENT_JANE =
    "UL3CC3CCCC Confirmed. Ksh200.00 sent to JANE  WANJIKU 0722123456 on 12/11/26 at 5:00 PM. New M-PESA balance is Ksh700.00. " +
        "Transaction cost, Ksh0.00."
private const val SENT_JANE_INTL =
    "UL4DD4DDDD Confirmed. Ksh150.00 sent to Jane W 254722123456 on 13/11/26 at 5:00 PM. New M-PESA balance is Ksh550.00. " +
        "Transaction cost, Ksh0.00."
private const val SENT_TO_PERSON_NAMED_MALI =
    "UL5EE5EEEE Confirmed. Ksh100.00 sent to MALI OTIENO 0711000999 on 13/11/26 at 6:00 PM. New M-PESA balance is Ksh450.00. " +
        "Transaction cost, Ksh0.00."
private const val AIRTIME =
    "UL6FF6FFFF confirmed.You bought Ksh50.00 of airtime on 13/11/26 at 8:00 PM.New M-PESA balance is Ksh400.00. Transaction cost, Ksh0.00."
private const val MPESA_TO_COOP =
    "UK7DR1OIW6 Confirmed. Ksh320.00 sent to Co-operative Bank Money Transfer for account 01103999888001 on 7/11/26 at 11:00 AM " +
        "New M-PESA balance is Ksh638.74. Transaction cost, Ksh0.00."
private const val COOP_FROM_MPESA =
    "Ref: AC54CB831326. Dear JOHN, your M-PESA to Account Deposit of KES 320 to account 01103999888001 was successful. " +
        "M-PESA Ref: UK7DR1OIW6."

class CategorizerTest {

    private fun known(p: Placement): Categorized {
        assertTrue("Expected Known, was $p", p is Placement.Known)
        return (p as Placement.Known).result
    }

    @Test fun unknownTillAsksWithFoodFuelOther() {
        val p = Categorizer().place(mpesa(TILL_CHRON))
        assertTrue(p is Placement.Ask)
        p as Placement.Ask
        assertEquals(listOf(Category.FOOD, Category.FUEL_TRANSPORT, Category.OTHER), p.choices)
        assertEquals("KSh 80.00 paid to Chron Investments. What was it?", p.prompt)
        assertEquals(MerchantKey("till:CHRON INVESTMENTS"), p.key)
    }

    @Test fun oneAnswerAppliesToEveryFuturePaymentThere() {
        val c = Categorizer()
        val ask = c.place(mpesa(TILL_CHRON)) as Placement.Ask
        c.learn(ask.key!!, Category.FOOD)
        val next = known(c.place(mpesa(TILL_CHRON_AGAIN)))
        assertEquals(Category.FOOD, next.category)
        assertEquals(Source.LEARNED, next.source)
    }

    @Test fun peopleAreIdentifiedByPhoneNumberNotName() {
        val c = Categorizer()
        val ask = c.place(mpesa(SENT_JANE)) as Placement.Ask
        assertEquals(MerchantKey("person:0722123456"), ask.key)
        c.learn(ask.key!!, Category.FAMILY)
        // Different spelling of the name, number written as 254…: still the same person.
        assertEquals(Category.FAMILY, known(c.place(mpesa(SENT_JANE_INTL))).category)
    }

    @Test fun builtInAndKeywordRules() {
        val kplc = known(Categorizer().place(mpesa(PAYBILL_KPLC)))
        assertEquals(Category.UTILITIES, kplc.category)
        assertEquals(Source.BUILT_IN, kplc.source)
        val wine = known(Categorizer().place(mpesa(PAYBILL_WINE)))
        assertEquals(Category.GOING_OUT, wine.category)
        assertEquals("The name contains \"WINE\"", wine.reason)
    }

    @Test fun directoryNeverAppliesToPeople() {
        // "MALI" is a savings product, but here it's a person's name.
        assertTrue(Categorizer().place(mpesa(SENT_TO_PERSON_NAMED_MALI)) is Placement.Ask)
    }

    @Test fun typeDecidesAirtime() {
        assertEquals(Category.AIRTIME_DATA, known(Categorizer().place(mpesa(AIRTIME))).category)
    }

    @Test fun mpesaToOwnBankIsATransferOnBothSides() {
        val pipeline = SmsPipeline()
        val bank = coop(pipeline, COOP_FROM_MPESA)
        val mp = mpesa(MPESA_TO_COOP)
        val c = Categorizer(linkedCodes = { setOf(bank.linkedCode!!) })
        assertEquals(Category.TRANSFER, known(c.place(mp)).category)
        assertEquals(Category.TRANSFER, known(c.place(bank)).category)
        assertTrue(!Category.TRANSFER.isSpending)
    }

    @Test fun paybillIntoOwnAccountIsTransferEvenWithoutBankMessage() {
        val c = Categorizer(isOwnAccount = { it == "01103999888001" })
        assertEquals(Category.TRANSFER, known(c.place(mpesa(MPESA_TO_COOP))).category)
    }

    @Test fun paybillAccountLevelAnswerFallsBackToBusinessLevel() {
        val c = Categorizer()
        c.learn(MerchantKey("paybill:KINGS CHOICE WINE"), Category.FOOD)
        assertEquals(Category.FOOD, known(c.place(mpesa(PAYBILL_WINE))).category)
    }
}

class QuestionPolicyTest {
    private val policy = QuestionPolicy()
    private val afternoon = LocalDateTime.of(2026, 11, 12, 14, 0)

    @Test fun pushesDuringTheDay() =
        assertEquals(QuestionPolicy.Decision.PUSH_NOW, policy.decide(ksh("250"), afternoon, 0))

    @Test fun waitsUntilMorningAtNight() =
        assertEquals(QuestionPolicy.Decision.HOLD_UNTIL_MORNING, policy.decide(ksh("250"), afternoon.withHour(23), 0))

    @Test fun neverMoreThanThreeADay() =
        assertEquals(QuestionPolicy.Decision.LIST_ONLY, policy.decide(ksh("250"), afternoon, 3))

    @Test fun smallAmountsDontPush() =
        assertEquals(QuestionPolicy.Decision.LIST_ONLY, policy.decide(ksh("20"), afternoon, 0))
}

class SafeToSpendTest {
    @Test fun dailyAllowanceUntilStipend() {
        val r = SafeToSpend.compute(
            SafeToSpend.Input(
                balances = ksh("6,000"), billsDueBeforePayday = ksh("1,500"), savingsSetAside = ksh("500"),
                fulizaOutstanding = ksh("300"), today = LocalDate.of(2026, 11, 11), nextIncome = LocalDate.of(2026, 11, 21),
            ),
        )
        assertEquals(ksh("3,700"), r.available)
        assertEquals(10, r.daysLeft)
        assertEquals(ksh("370"), r.perDay)
        assertEquals(SafeToSpend.Status.COMFORTABLE, r.status)
    }

    @Test fun spendingTodayReducesTodayNotTheDailyFigure() {
        val r = SafeToSpend.compute(
            SafeToSpend.Input(
                ksh("3,500"), Money.ZERO, Money.ZERO, Money.ZERO,
                LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 21), spentToday = ksh("500"),
            ),
        )
        assertEquals(ksh("400"), r.perDay)
        assertEquals(-ksh("100"), r.leftToday)
    }

    @Test fun shortWhenBillsExceedBalance() {
        val r = SafeToSpend.compute(
            SafeToSpend.Input(
                ksh("800"), ksh("1,000"), Money.ZERO, Money.ZERO,
                LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 21),
            ),
        )
        assertEquals(Money.ZERO, r.perDay)
        assertEquals(SafeToSpend.Status.SHORT, r.status)
    }
}

class FraudSenderTest {
    private fun run(sender: String, body: String) = SmsPipeline().process(RawSms(sender, body, NOW))

    @Test fun lookalikeSenderNamesAreFlagged() {
        val body = "UK1ZZ1ZZZZ Confirmed.You have received Ksh2,000.00 from ALICE 0711222333 on 10/11/26 at 1:00 PM New M-PESA balance is Ksh2,100.00."
        for (fake in listOf("MPESA-KE", "M-PESA1", "MPESSA", "C0-OPBANK", "EQUITYBANKKE")) {
            val o = run(fake, body)
            assertTrue("$fake -> $o", o is ParseOutcome.ScamAlert)
            assertTrue(ScamSignal.LOOKALIKE_SENDER in (o as ParseOutcome.ScamAlert).signals)
        }
        assertEquals(Provider.MPESA, (run("MPESA-KE", body) as ParseOutcome.ScamAlert).imitates)
        assertEquals(Provider.COOP, (run("C0-OPBANK", body) as ParseOutcome.ScamAlert).imitates)
    }

    @Test fun loanSpamFromCompanyNamesIsNeverRecorded() {
        val spam = "Dear customer, you qualify for a loan of up to Ksh 50,000 from MoneyMake. Apply now at moneymake.co.ke"
        val o = run("MONEYMAKE", spam)
        assertTrue(o !is ParseOutcome.Parsed)
        assertNull((o as? ParseOutcome.ScamAlert)?.imitates)
    }

    @Test fun loanWithUpfrontFeeIsAScam() {
        val scam = "Congrats! Your loan of Ksh 30,000 is approved. Pay a processing fee of Ksh 450 to 0799111222 to receive your loan today."
        val o = run("QUICKCASH", scam)
        assertTrue("was $o", o is ParseOutcome.ScamAlert)
        val signals = (o as ParseOutcome.ScamAlert).signals
        assertTrue(ScamSignal.UPFRONT_FEE in signals)
        assertTrue(ScamSignal.LOAN_BAIT in signals)
    }

    @Test fun genuineSendersAreNotLookalikes() {
        val body = "UK1ZZ1ZZZZ Confirmed.You have received Ksh2,000.00 from ALICE 0711222333 on 10/11/26 at 1:00 PM New M-PESA balance is Ksh2,100.00."
        assertTrue(run("MPESA", body) is ParseOutcome.Parsed)
        assertTrue(run("M-PESA", body) is ParseOutcome.Parsed)
    }
}
