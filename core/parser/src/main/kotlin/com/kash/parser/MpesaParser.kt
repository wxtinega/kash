package com.kash.parser

import java.time.LocalDateTime

/**
 * One message format. Kept as plain data (type + pattern string) so the rule table can later
 * be shipped as a signed config file and updated without an app release when Safaricom
 * changes its wording.
 *
 * Patterns may use the named groups:
 *  - amt  (required unless [needsAmount] is false)
 *  - who  counterparty name, optionally followed by a phone number
 *  - acct paybill account number
 *  - ref  referenced transaction code (reversals)
 */
data class RuleSpec(
    val type: TxType,
    val pattern: String,
    val needsBalance: Boolean = true,
    val needsAmount: Boolean = true,
)

/**
 * Layer 2 of the filter: does a message from MPESA match a known format?
 * Layer 3 (balance chain) lives in [BalanceChain]; layer 4 (duplicates) in [SmsPipeline].
 */
class MpesaParser(rules: List<RuleSpec> = DEFAULT_RULES) {

    private val compiled = rules.map { it to Regex(it.pattern, RegexOption.IGNORE_CASE) }

    fun parse(body: String, fallbackTime: LocalDateTime): ParseOutcome {
        if (FAILED.containsMatchIn(body)) return ParseOutcome.Ignored(IgnoreReason.FAILED_TRANSACTION)

        val code = Fields.mpesaCode(body)
            ?: return ParseOutcome.Ignored(IgnoreReason.NOT_A_TRANSACTION)

        for ((rule, regex) in compiled) {
            val m = regex.find(body) ?: continue
            return build(rule, m, code, body, fallbackTime)
        }
        // Starts like a real confirmation but no rule fits: a new or changed format.
        return ParseOutcome.NeedsReview(Provider.MPESA, "Unrecognised M-Pesa format")
    }

    private fun build(
        rule: RuleSpec,
        m: MatchResult,
        code: String,
        body: String,
        fallbackTime: LocalDateTime,
    ): ParseOutcome {
        val amount = m.groups.group("amt")?.let { Money.parseNumber(it) }
        val (who, phone) = m.groups.group("who")?.let { Fields.splitNameAndPhone(it) } ?: (null to null)
        val account = Sanitizer.displayName(m.groups.group("acct"))
        val ref = m.groups.group("ref")
        val balance = Fields.balance(body)
        val fee = when (rule.type) {
            TxType.FULIZA_LOAN -> Fields.fulizaFee(body)
            else -> Fields.fee(body)
        } ?: Money.ZERO
        val time = Fields.dateTime(body)

        val partial = Transaction(
            code = code,
            provider = Provider.MPESA,
            type = rule.type,
            amount = amount ?: Money.ZERO,
            fee = fee,
            counterparty = who,
            counterpartyPhone = phone,
            accountNumber = account,
            occurredAt = time ?: fallbackTime,
            balanceAfter = balance,
            reversesCode = ref,
            fulizaOutstanding = if (rule.type == TxType.FULIZA_LOAN || rule.type == TxType.FULIZA_REPAY) {
                Fields.fulizaOutstanding(body)
            } else null,
            confidence = if (time != null) Confidence.HIGH else Confidence.MEDIUM,
        )

        val problem = when {
            rule.needsAmount && (amount == null || !amount.isPositive) -> "Amount missing or invalid"
            rule.needsBalance && balance == null -> "Balance missing"
            fee > MAX_FEE -> "Fee is implausibly large"
            amount != null && rule.type == TxType.SENT && who == null -> "Recipient missing"
            else -> null
        }
        return if (problem == null) ParseOutcome.Parsed(partial)
        else ParseOutcome.NeedsReview(Provider.MPESA, problem, partial.copy(confidence = Confidence.LOW))
    }

    private fun MatchGroupCollection.group(name: String): String? =
        try {
            this[name]?.value
        } catch (e: IllegalArgumentException) {
            null // pattern doesn't define this group
        }

    companion object {
        private val MAX_FEE = Money.ofShillings(10_000)
        private val FAILED = Regex("""^\s*Failed\b|insufficient funds|could not be completed""", RegexOption.IGNORE_CASE)
        private const val A = Fields.AMT

        /** Order matters: more specific formats first. */
        val DEFAULT_RULES = listOf(
            RuleSpec(TxType.REVERSAL, """Transaction (?<ref>[A-Z][A-Z0-9]{9}) has been reversed""", needsAmount = false),
            RuleSpec(TxType.FULIZA_LOAN, """Fuliza M-PESA amount is\s*$A""", needsBalance = false),
            RuleSpec(TxType.FULIZA_REPAY, """$A from your M-PESA has been used to (?:fully |partially )?pay your outstanding Fuliza"""),
            RuleSpec(TxType.MSHWARI_WITHDRAW, """$A transferred from M-Shwari account"""),
            RuleSpec(TxType.MSHWARI_SAVE, """$A transferred to M-Shwari account"""),
            RuleSpec(TxType.AIRTIME, """You bought $A of airtime"""),
            RuleSpec(TxType.RECEIVED, """You have received $A from (?<who>.+?) on\s*\d"""),
            RuleSpec(TxType.WITHDRAW, """Withdraw\s*$A from (?<who>.+?)\s*New M-PESA balance"""),
            RuleSpec(TxType.DEPOSIT, """Give\s*$A cash to (?<who>.+?)\s*New M-PESA balance"""),
            RuleSpec(TxType.PAYBILL, """$A sent to (?<who>.+?) for account (?<acct>.+?) on\s*\d"""),
            RuleSpec(TxType.SENT, """$A sent to (?<who>.+?) on\s*\d"""),
            RuleSpec(TxType.TILL, """$A paid to (?<who>.+?)\.? on\s*\d"""),
        )
    }
}
