package com.kash.parser

import java.security.MessageDigest
import java.time.LocalDateTime

/** Answers "is this (possibly masked) account number one of the user's own accounts?" */
fun interface OwnAccounts {
    /** true = theirs, false = not theirs, null = don't know yet (ask the user once, then remember). */
    fun isOwn(maskedOrFull: String): Boolean?

    companion object {
        val UNKNOWN = OwnAccounts { null }

        /** Matches masks like "01103****001" against full numbers like "01103221714001". */
        fun of(numbers: Collection<String>) = OwnAccounts { candidate ->
            val c = candidate.filter { it.isDigit() || it == '*' }
            numbers.any { full -> maskMatches(c, full.filter(Char::isDigit)) }
        }

        fun maskMatches(mask: String, full: String): Boolean {
            if (!mask.contains('*')) return mask == full
            val head = mask.substringBefore('*')
            val tail = mask.substringAfterLast('*')
            return full.length >= head.length + tail.length && full.startsWith(head) && full.endsWith(tail)
        }
    }
}

/**
 * Own accounts learned from messages (e.g. "M-PESA to Account Deposit ... to account 0110...")
 * plus any the user confirms. Unknown accounts return null so the app can ask once.
 */
class LearnedAccounts(initial: Collection<String> = emptyList()) : OwnAccounts {
    private val numbers = LinkedHashSet(initial.map { n -> n.filter(Char::isDigit) })
    @Synchronized fun add(number: String) { numbers += number.filter(Char::isDigit) }
    @Synchronized fun all(): Set<String> = numbers.toSet()
    @Synchronized override fun isOwn(maskedOrFull: String): Boolean? {
        val c = maskedOrFull.filter { it.isDigit() || it == '*' }
        return if (numbers.any { OwnAccounts.maskMatches(c, it) }) true else null
    }
}

/**
 * One bank message format. Named groups:
 *  amt   amount in KES           who  counterparty       acct  account number
 *  payer who paid (when someone else pays into the user's account)
 *  cur / famt  currency and amount for card purchases
 */
data class BankRule(
    val id: String,
    val type: TxType,
    val pattern: String,
    /** Counterparty label, e.g. "Your M-Pesa". "{who}" is replaced with the captured value. */
    val label: String? = null,
    val pending: Boolean = false,
)

/**
 * Bank alerts. Banks with known formats (from real messages) get specific rules;
 * everything else falls back to a cautious generic reader, and ambiguity goes to review.
 */
class BankParser(
    private val rules: Map<Provider, List<BankRule>> = DEFAULT_RULES,
    private val ownAccounts: OwnAccounts = LearnedAccounts(),
) {
    private val compiled = rules.mapValues { (_, list) -> list.map { it to Regex(it.pattern, RegexOption.IGNORE_CASE) } }

    fun parse(provider: Provider, sender: String, body: String, fallbackTime: LocalDateTime): ParseOutcome {
        if (FAILED.containsMatchIn(body)) return ParseOutcome.Ignored(IgnoreReason.FAILED_TRANSACTION)

        // A bank telling you an organisation paid you BY M-PESA (e.g. Citi on behalf of a university).
        // The money arrives in M-Pesa, which sends its own message: counting this too would double it.
        if (PAID_TO_YOUR_MPESA.containsMatchIn(body)) return ParseOutcome.Ignored(IgnoreReason.NOT_A_TRANSACTION)

        val balance = BALANCE.find(body)?.let { Money.parseNumber(it.groupValues[1]) }
        val bankRef = REFERENCE.find(body)?.groupValues?.get(1)
        val mpesaRef = MPESA_REF.find(body)?.groupValues?.get(1)
        val time = Fields.bankDateTime(body)
        val code = "${provider.name}-" + (bankRef ?: mpesaRef?.let { "MP$it" } ?: "H" + shortHash("$sender|$body"))

        val base = Transaction(
            code = code, provider = provider, type = TxType.BANK_DEBIT, amount = Money.ZERO, fee = Money.ZERO,
            counterparty = null, counterpartyPhone = null, accountNumber = null,
            occurredAt = time ?: fallbackTime, balanceAfter = balance, linkedCode = mpesaRef,
            confidence = if (time != null) Confidence.HIGH else Confidence.MEDIUM,
        )

        for ((rule, regex) in compiled[provider].orEmpty()) {
            val m = regex.find(body) ?: continue
            return fromRule(provider, rule, m, base)
        }
        return generic(provider, body, base)
    }

    /** True when one of this bank's exact (real-message) formats matches, not just the generic reader. */
    fun matchesKnownFormat(provider: Provider, body: String): Boolean =
        compiled[provider].orEmpty().any { (_, r) -> r.containsMatchIn(body) }

    private fun fromRule(provider: Provider, rule: BankRule, m: MatchResult, base: Transaction): ParseOutcome {
        val g = m.groups
        val who = g.named("who")?.substringBefore('>')?.let { Fields.splitNameAndPhone(it) }
        val payer = Sanitizer.displayName(g.named("payer"))
        val acct = g.named("acct")?.trim()

        var tx = base.copy(
            type = rule.type,
            counterparty = payer
                ?: rule.label?.let { l -> Sanitizer.displayName(l.replace("{who}", who?.first.orEmpty())) }
                ?: who?.first,
            counterpartyPhone = who?.second,
            accountNumber = Sanitizer.displayName(acct),
            pending = rule.pending,
        )

        // Card purchase in a foreign currency: KES amount isn't in the message.
        val cur = g.named("cur")?.uppercase()
        if (cur != null && cur != "KES" && cur != "KSH") {
            tx = tx.copy(foreignAmount = "$cur ${g.named("famt")}", confidence = Confidence.LOW)
            return ParseOutcome.NeedsReview(provider, "Paid in $cur; KES amount not stated", tx)
        }

        val amount = (g.named("amt") ?: g.named("famt"))?.let { Money.parseNumber(it) }
        if (amount == null || !amount.isPositive) {
            return ParseOutcome.NeedsReview(provider, "Amount missing or invalid", tx.copy(confidence = Confidence.LOW))
        }
        tx = tx.copy(amount = amount)

        // Deposits from the user's own M-Pesa reveal their own account number.
        if (rule.id == "coop-from-mpesa" && acct != null) (ownAccounts as? LearnedAccounts)?.add(acct)

        // "Dear X, you have sent ... to <account>": a credit only if that account is the user's.
        if (rule.id == "coop-paid-in" && acct != null) {
            return when (ownAccounts.isOwn(acct)) {
                true -> ParseOutcome.Parsed(tx)
                false -> ParseOutcome.Ignored(IgnoreReason.NOT_A_TRANSACTION) // receipt for an M-Pesa payment already recorded
                null -> ParseOutcome.NeedsReview(provider, "Is account $acct yours?", tx)
            }
        }
        return ParseOutcome.Parsed(tx)
    }

    private fun generic(provider: Provider, body: String, base: Transaction): ParseOutcome {
        val balanceMatch = BALANCE.find(body)
        val rest = if (balanceMatch != null) body.removeRange(balanceMatch.range) else body

        val amount = AMOUNT.find(rest)?.let { Money.parseNumber(it.groupValues[1]) }
            ?: return ParseOutcome.Ignored(IgnoreReason.NOT_A_TRANSACTION) // OTPs, promos, notices

        val isCredit = CREDIT.containsMatchIn(rest)
        val isDebit = DEBIT.containsMatchIn(rest)
        val type = when {
            isCredit && !isDebit -> TxType.BANK_CREDIT
            isDebit && !isCredit -> TxType.BANK_DEBIT
            else -> null
        }
        val confidence = if (base.balanceAfter != null) Confidence.MEDIUM else Confidence.LOW
        val tx = base.copy(type = type ?: TxType.BANK_DEBIT, amount = amount, confidence = confidence)

        return when {
            type == null -> ParseOutcome.NeedsReview(provider, "Can't tell if money came in or went out", tx.copy(confidence = Confidence.LOW))
            !amount.isPositive -> ParseOutcome.NeedsReview(provider, "Amount missing or invalid", tx)
            else -> ParseOutcome.Parsed(tx)
        }
    }

    private fun MatchGroupCollection.named(name: String): String? =
        try {
            this[name]?.value
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun shortHash(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }

    companion object {
        private const val CUR = """(?:Ksh|KES|Kshs)\.?\s?"""
        private const val NUM = """((?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?)"""
        private const val A = Fields.AMT
        private const val FNUM = """(?<famt>(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?)"""

        private val BALANCE = Regex("""\b(?:avail(?:able)?\.?\s*)?bal(?:ance)?\.?\s*(?:is)?\s*:?\s*$CUR$NUM""", RegexOption.IGNORE_CASE)
        private val AMOUNT = Regex("""$CUR$NUM""", RegexOption.IGNORE_CASE)
        private val CREDIT = Regex("""\b(credited|received|deposited|deposit of|has been paid into)\b""", RegexOption.IGNORE_CASE)
        private val DEBIT = Regex("""\b(debited|withdrawn|withdrawal|paid to|payment to|purchase|spent|sent to|transferred to)\b""", RegexOption.IGNORE_CASE)
        /** Bank's own reference; "M-PESA Ref" is captured separately as the linked M-Pesa code. */
        private val REFERENCE = Regex("""(?<!PESA )\b(?:Ref(?:erence)?|Tx\.?\s*ID)\.?\s*(?:No\.?)?\s*:?\s*([A-Z0-9]{6,20})\b""", RegexOption.IGNORE_CASE)
        private val MPESA_REF = Regex("""M-?PESA Ref\.?\s*(?:No\.?)?\s*:?\s*([A-Z][A-Z0-9]{9})\b""", RegexOption.IGNORE_CASE)
        private val FAILED = Regex("""\b(failed|declined|unsuccessful|insufficient funds|reversed)\b""", RegexOption.IGNORE_CASE)
        private val PAID_TO_YOUR_MPESA = Regex("""You have received\s*(?:Ksh|KES|Kshs)\.?\s?[\d,.]+ from .+? with M-?Pesa Ref""", RegexOption.IGNORE_CASE)

        /** Co-op Bank formats, taken from real messages (Nov 2026). */
        val COOP_RULES = listOf(
            BankRule(
                "coop-from-mpesa", TxType.BANK_CREDIT,
                """M-PESA to Account Deposit of\s*$A to account (?<acct>\d+) was successful""",
                label = "From your M-Pesa",
            ),
            BankRule(
                "coop-to-own-mpesa", TxType.BANK_DEBIT,
                """MPESA transfer of\s*$A to your mobile""",
                label = "To your M-Pesa",
            ),
            BankRule(
                "coop-to-mpesa", TxType.BANK_DEBIT,
                """MPESA transfer of\s*$A to (?<who>.+?\s*-?\s*(?:\+?254|0)\d{9}) at""",
            ),
            BankRule(
                "coop-paybill", TxType.BANK_DEBIT,
                """request to pay\s*$A to Paybill Number (?<who>\d+) and A/c (?<acct>\S+) on""",
                label = "Paybill {who}",
                pending = true,
            ),
            BankRule(
                "coop-card", TxType.BANK_DEBIT,
                """purchase of (?<cur>[A-Z]{3})\.?\s*$FNUM from (?<who>.+?)\s+at\s+\d""",
            ),
            BankRule(
                "coop-paid-in", TxType.BANK_CREDIT,
                """Dear (?<payer>[^,]+), you have sent\s*$A to .+? (?<acct>\d[\d*]{5,})\s*on""",
            ),
        )

        /**
         * Co-op Bank's money market fund (Co-op Trust) messages come on the same sender as the bank, e.g.
         *   "Dear NAME, We confirm a deposit of 700.00 to your CL42326. Tx ID: CB1177155. For enquiries call 0711049000"
         *   "Dear NAME, Your redemption request for Kes 3,000.00 has been processed and sent to your account."
         * They confirm money already moved by M-Pesa/bank, so they update savings only.
         */
        private const val NUM_AMT = """(?:(?:Ksh|KES|Kshs)\.?\s?)?(?<amt>(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?)"""
        val COOP_FUND_RULES = listOf(
            BankRule(
                "coop-fund-deposit", TxType.FUND_DEPOSIT,
                """We confirm a deposit of\s*$NUM_AMT to your (?<acct>[A-Z]{1,4}\d{3,})""",
                label = "Co-op Money Market Fund",
            ),
            BankRule(
                "coop-fund-redemption", TxType.FUND_REDEMPTION,
                """redemption request for\s*$NUM_AMT has been processed""",
                label = "Co-op Money Market Fund",
            ),
        )

        val DEFAULT_RULES: Map<Provider, List<BankRule>> = mapOf(Provider.COOP to COOP_FUND_RULES + COOP_RULES)
    }
}
