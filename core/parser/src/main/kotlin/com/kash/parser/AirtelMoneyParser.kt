package com.kash.parser

import java.security.MessageDigest
import java.time.LocalDateTime

/**
 * Airtel Money (Kenya). Exact rules come from real messages (Oct 2026):
 *
 *   33T16RW138U. Ksh 90 sent to NAME 2547XXXXXXXX on 30/10/26 at 02:11 PM. Fee: Ksh 0. Bal: Ksh 0.0. MPESA ID: UJU6YBTOQQ
 *   TID:G3SW6PY4RFG. Received Ksh 100 from NAME 2547XXXXXXXX on 27/10/26 04:20 PM. Bal:Ksh 105.0 Sender TID:UJRFPBLVVF.
 *   73STEWM8DAH. Ksh 60 paid to Lipa Na Mpesa via Airtel Money on 25/10/26 at 04:04 PM. Fee: Ksh 0. Bal: Ksh 15.0. MPESA ID: UJPS017D58
 *   56705560258 Confirmed. Bundle purchase successful of Ksh 15 via Airtel Networks Kenya Ltd on 29/10/26 at 07:42 PM. Bal: Ksh 90.0.
 *
 * Anything else falls back to a cautious generic reader, which only counts a message that has a
 * transaction ID or a balance (so a promo quoting a price can't become spending).
 */
class AirtelMoneyParser {

    private data class Rule(val type: TxType, val regex: Regex, val label: String? = null)

    fun parse(sender: String, body: String, fallbackTime: LocalDateTime): ParseOutcome {
        if (FAILED.containsMatchIn(body)) return ParseOutcome.Ignored(IgnoreReason.FAILED_TRANSACTION)

        val balance = BALANCE.find(body)?.let { Money.parseNumber(it.groupValues[1]) }
        val fee = FEE.find(body)?.let { Money.parseNumber(it.groupValues[1]) } ?: Money.ZERO
        val linked = MPESA_ID.find(body)?.groupValues?.get(1)
        val time = Fields.dateTime(body) ?: Fields.bankDateTime(body)

        for (rule in RULES) {
            val m = rule.regex.find(body) ?: continue
            val amount = m.groups.named("amt")?.let { Money.parseNumber(it) } ?: continue
            val id = m.groups.named("id")
            val (who, phone) = when {
                rule.label != null -> rule.label to null
                else -> m.groups.named("who")?.let { Fields.splitNameAndPhone(it) } ?: (null to null)
            }
            val tx = Transaction(
                code = "AIRTEL-" + (id ?: "H" + shortHash("$sender|$body")),
                provider = Provider.AIRTEL_MONEY,
                type = rule.type,
                amount = amount,
                fee = fee,
                counterparty = who,
                counterpartyPhone = phone,
                accountNumber = null,
                occurredAt = time ?: fallbackTime,
                balanceAfter = balance,
                linkedCode = linked,
                confidence = if (time != null && balance != null) Confidence.HIGH else Confidence.MEDIUM,
            )
            return if (amount.isPositive) ParseOutcome.Parsed(tx)
            else ParseOutcome.NeedsReview(Provider.AIRTEL_MONEY, "Amount missing or invalid", tx)
        }
        return generic(sender, body, balance, fee, linked, time ?: fallbackTime)
    }

    /** For Airtel formats not seen yet. */
    private fun generic(sender: String, body: String, balance: Money?, fee: Money, linked: String?, time: LocalDateTime): ParseOutcome {
        val id = LEADING_ID.find(body)?.groupValues?.get(1) ?: TX_ID.find(body)?.groupValues?.get(1)?.trimEnd('.')
        if (id == null && balance == null) return ParseOutcome.Ignored(IgnoreReason.NOT_A_TRANSACTION)

        var rest = body
        listOfNotNull(BALANCE.find(body), FEE.find(body)).sortedByDescending { it.range.first }
            .forEach { rest = rest.removeRange(it.range) }
        val amount = AMOUNT.find(rest)?.let { Money.parseNumber(it.groupValues[1]) }
            ?: return ParseOutcome.Ignored(IgnoreReason.NOT_A_TRANSACTION)

        val type = when {
            AIRTIME.containsMatchIn(rest) -> TxType.AIRTIME
            WITHDRAW.containsMatchIn(rest) -> TxType.WITHDRAW
            RECEIVED.containsMatchIn(rest) -> TxType.RECEIVED
            PAID.containsMatchIn(rest) -> TxType.TILL
            SENT.containsMatchIn(rest) -> TxType.SENT
            else -> null
        }
        val tx = Transaction(
            code = "AIRTEL-" + (id ?: "H" + shortHash("$sender|$body")),
            provider = Provider.AIRTEL_MONEY,
            type = type ?: TxType.OTHER_OUT,
            amount = amount,
            fee = fee,
            counterparty = null,
            counterpartyPhone = null,
            accountNumber = null,
            occurredAt = time,
            balanceAfter = balance,
            linkedCode = linked,
            confidence = Confidence.LOW,
        )
        return ParseOutcome.NeedsReview(Provider.AIRTEL_MONEY, "New Airtel Money message format", tx)
    }

    private fun MatchGroupCollection.named(name: String): String? =
        try { this[name]?.value } catch (e: IllegalArgumentException) { null }

    private fun shortHash(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

    companion object {
        /** Shown when Airtel doesn't say which shop was paid; never used to auto-file future payments. */
        const val UNNAMED_LIPA_NA_MPESA = "Lipa na M-Pesa (shop not named)"

        private const val CUR = """(?:Ksh|KES|Kshs)\.?\s?"""
        private const val NUM = """((?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?)"""
        private const val A = Fields.AMT
        private const val ID = """^\s*(?:TID:\s*)?(?<id>[A-Z0-9]{8,16})\.?\s*(?:Confirmed\.?)?\s*"""
        private val o = setOf(RegexOption.IGNORE_CASE)

        private val RULES = listOf(
            Rule(TxType.PAYBILL, Regex("""${ID}Bundle purchase successful of\s*$A via""", o), label = "Airtel data bundles"),
            Rule(TxType.RECEIVED, Regex("""${ID}Received\s*$A from (?<who>.+?) on\s*\d""", o)),
            Rule(TxType.TILL, Regex("""${ID}$A paid to Lipa Na M-?pesa(?: via Airtel Money)? on\s*\d""", o), label = UNNAMED_LIPA_NA_MPESA),
            Rule(TxType.TILL, Regex("""${ID}$A paid to (?<who>.+?) on\s*\d""", o)),
            Rule(TxType.SENT, Regex("""${ID}$A sent to (?<who>.+?) on\s*\d""", o)),
        )

        private val AMOUNT = Regex("$CUR$NUM", o)
        private val BALANCE = Regex("""\bBal(?:ance)?\.?(?: is)?\s*:?\s*$CUR$NUM""", o)
        private val FEE = Regex("""\b(?:Fee|Transaction cost|Charges?)\s*:?\s*(?:is|of)?\s*$CUR$NUM""", o)
        private val MPESA_ID = Regex("""\b(?:MPESA ID|Sender TID)\s*:\s*([A-Z0-9]{8,12})""", o)
        private val LEADING_ID = Regex("""^\s*(?:TID:\s*)?([A-Z0-9]{8,16})\.?\s""")
        private val TX_ID = Regex("""\b(?:Trans(?:action)?\.?\s*ID|Txn\.?\s*ID)\s*[:.]?\s*([A-Z0-9][A-Z0-9.]{5,30})""", o)
        private val FAILED = Regex("""\b(failed|unsuccessful|declined|insufficient)\b""", o)

        private val RECEIVED = Regex("""\b(received|credited)\b""", o)
        private val WITHDRAW = Regex("""\bwithdr(?:aw|ew|awn|awal)\b""", o)
        private val AIRTIME = Regex("""\bairtime\b""", o)
        private val PAID = Regex("""\bpaid\b|\bpayment\b""", o)
        private val SENT = Regex("""\b(sent|transferred)\b""", o)
    }
}
