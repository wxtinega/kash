package com.kash.parser

import java.security.MessageDigest
import java.time.LocalDateTime

/**
 * Airtel Money (Kenya). Built from published examples, not yet from real messages, so it reads
 * the common pieces (amount, direction, cost, new balance, transaction ID, who) rather than one
 * exact sentence. Anything it can't place confidently goes to review with what it found.
 *
 * TODO: replace with exact rules (like MpesaParser's) once we have real anonymised samples.
 */
class AirtelMoneyParser {

    fun parse(sender: String, body: String, fallbackTime: LocalDateTime): ParseOutcome {
        if (FAILED.containsMatchIn(body)) return ParseOutcome.Ignored(IgnoreReason.FAILED_TRANSACTION)

        // Take the balance and cost clauses out first so their numbers aren't read as the amount.
        val balanceMatch = BALANCE.find(body)
        val feeMatch = FEE.find(body)
        var rest = body
        listOfNotNull(balanceMatch, feeMatch).sortedByDescending { it.range.first }.forEach { rest = rest.removeRange(it.range) }

        val amount = AMOUNT.find(rest)?.let { Money.parseNumber(it.groupValues[1]) }
            ?: return ParseOutcome.Ignored(IgnoreReason.NOT_A_TRANSACTION) // promos, PIN reminders

        val balance = balanceMatch?.let { Money.parseNumber(it.groupValues[1]) }
        val fee = feeMatch?.let { Money.parseNumber(it.groupValues[1]) } ?: Money.ZERO

        val type = when {
            AIRTIME.containsMatchIn(rest) -> TxType.AIRTIME
            WITHDRAW.containsMatchIn(rest) -> TxType.WITHDRAW
            DEPOSIT.containsMatchIn(rest) -> TxType.DEPOSIT
            RECEIVED.containsMatchIn(rest) -> TxType.RECEIVED
            PAID.containsMatchIn(rest) -> TxType.TILL
            SENT.containsMatchIn(rest) -> TxType.SENT
            else -> null
        }

        val whoRaw = when (type) {
            TxType.RECEIVED, TxType.DEPOSIT -> FROM_WHO.find(rest)?.groupValues?.get(1)
            TxType.SENT, TxType.TILL, TxType.WITHDRAW -> TO_WHO.find(rest)?.groupValues?.get(1)
            else -> null
        }?.replace("(", " ")?.replace(")", " ")
        val (who, phone) = whoRaw?.let { Fields.splitNameAndPhone(it.trim()) } ?: (null to null)

        val id = TX_ID.find(body)?.groupValues?.get(1)?.trimEnd('.')
        val code = "AIRTEL-" + (id ?: "H" + shortHash("$sender|$body"))
        val time = Fields.dateTime(body) ?: Fields.bankDateTime(body)

        val tx = Transaction(
            code = code,
            provider = Provider.AIRTEL_MONEY,
            type = type ?: TxType.OTHER_OUT,
            amount = amount,
            fee = fee,
            counterparty = who,
            counterpartyPhone = phone,
            accountNumber = null,
            occurredAt = time ?: fallbackTime,
            balanceAfter = balance,
            confidence = if (balance != null && id != null) Confidence.MEDIUM else Confidence.LOW,
        )
        return when {
            type == null -> ParseOutcome.NeedsReview(Provider.AIRTEL_MONEY, "Can't tell if money came in or went out", tx)
            !amount.isPositive -> ParseOutcome.NeedsReview(Provider.AIRTEL_MONEY, "Amount missing or invalid", tx)
            else -> ParseOutcome.Parsed(tx)
        }
    }

    private fun shortHash(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

    companion object {
        private const val CUR = """(?:Ksh|KES|Kshs)\.?\s?"""
        private const val NUM = """((?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?)"""
        private val opts = setOf(RegexOption.IGNORE_CASE)

        private val AMOUNT = Regex("$CUR$NUM", opts)
        private val BALANCE = Regex("""(?:new |airtel money |available )*bal(?:ance)?\.?(?: is)?\s*:?\s*$CUR$NUM""", opts)
        private val FEE = Regex("""(?:transaction |txn )?(?:cost|charges?|fees?)\s*(?:is|of|:)?\s*$CUR$NUM""", opts)
        private val TX_ID = Regex("""\b(?:Trans(?:action)?\.?\s*ID|TID|Txn\.?\s*ID|Ref(?:erence)?(?:\s*No)?)\s*[:.]?\s*([A-Z0-9][A-Z0-9.]{5,30})""", opts)
        private val FAILED = Regex("""\b(failed|unsuccessful|declined|insufficient)\b""", opts)

        private val RECEIVED = Regex("""\b(received|credited)\b""", opts)
        private val DEPOSIT = Regex("""\bdeposit(?:ed)?\b""", opts)
        private val WITHDRAW = Regex("""\bwithdr(?:aw|ew|awn|awal)\b""", opts)
        private val AIRTIME = Regex("""\bairtime\b""", opts)
        private val PAID = Regex("""\bpaid\b|\bpayment\b""", opts)
        private val SENT = Regex("""\b(sent|transferred)\b""", opts)

        private val TO_WHO = Regex("""\bto\s+(.+?)(?=\s+on\s+\d|\s+at\s+\d|\.\s|,|$)""", opts)
        private val FROM_WHO = Regex("""\bfrom\s+(.+?)(?=\s+on\s+\d|\s+at\s+\d|\.\s|,|$)""", opts)
    }
}
