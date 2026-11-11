package com.kash.parser

import java.time.LocalDateTime
import java.time.ZoneId

/** Remembers which transaction codes have been recorded. Backed by the encrypted database in the app. */
interface SeenStore {
    /** Returns true if the code was new and has now been recorded. */
    fun markIfNew(code: String): Boolean
}

private val PHONE_LIKE = Regex("""^\+?\d{5,15}$""")

class InMemorySeenStore : SeenStore {
    private val seen = HashSet<String>()
    @Synchronized override fun markIfNew(code: String): Boolean = seen.add(code)
}

/**
 * The full filter, in order:
 *  1. sanitise the message (hostile input)
 *  2. check the sender (trusted ID vs ordinary number)
 *  3. match a known format (or score as a scam if untrusted)
 *  4. drop duplicates by transaction code
 * Balance-chain checks run over stored transactions via [BalanceChain].
 */
class SmsPipeline(
    private val senders: SenderPolicy = SenderPolicy.default(),
    private val seen: SeenStore = InMemorySeenStore(),
    private val mpesa: MpesaParser = MpesaParser(),
    private val banks: BankParser = BankParser(),
    private val airtel: AirtelMoneyParser = AirtelMoneyParser(),
    private val zone: ZoneId = ZoneId.of("Africa/Nairobi"),
) {

    fun process(sms: RawSms): ParseOutcome {
        val body = Sanitizer.cleanBody(sms.body) ?: return ParseOutcome.Ignored(IgnoreReason.MALFORMED)
        val fallbackTime = LocalDateTime.ofInstant(sms.receivedAt, zone)

        val outcome = when (val sender = senders.classify(sms.sender)) {
            is SenderClass.Trusted -> when (sender.provider) {
                Provider.MPESA -> mpesa.parse(body, fallbackTime)
                Provider.AIRTEL_MONEY -> airtel.parse(sms.sender, body, fallbackTime)
                else -> banks.parse(sender.provider, sms.sender, body, fallbackTime)
            }
            SenderClass.PersonalNumber, SenderClass.Unknown -> untrusted(sms.sender, body, fallbackTime)
        }

        if (outcome is ParseOutcome.Parsed && !seen.markIfNew(outcome.tx.code)) {
            return ParseOutcome.Duplicate(outcome.tx.code)
        }
        // Banks often send two messages about one M-Pesa movement (Co-op: a receipt "you have sent…"
        // AND "M-PESA to Account Deposit…"). Same bank + same M-Pesa ref + same amount = one movement.
        val linkTx = (outcome as? ParseOutcome.Parsed)?.tx ?: (outcome as? ParseOutcome.NeedsReview)?.partial
        if (linkTx != null && linkTx.provider.isBank && linkTx.linkedCode != null &&
            !seen.markIfNew("LINK|${linkTx.provider}|${linkTx.linkedCode}|${linkTx.amount.cents}")
        ) {
            return ParseOutcome.Duplicate(linkTx.code)
        }
        return when (outcome) {
            is ParseOutcome.Parsed -> outcome.copy(tx = outcome.tx.copy(receivedAt = sms.receivedAt))
            is ParseOutcome.NeedsReview -> {
                val partial = outcome.partial
                if (partial != null && !seen.markIfNew(partial.code)) ParseOutcome.Duplicate(partial.code)
                else outcome.copy(partial = partial?.copy(receivedAt = sms.receivedAt))
            }
            else -> outcome
        }
    }

    private fun untrusted(rawSender: String, body: String, fallbackTime: LocalDateTime): ParseOutcome {
        val imitates = senders.lookalikeOf(rawSender)
        // A registered (alphanumeric) sender whose name looks like a bank AND whose message matches that
        // bank's exact formats is probably the bank's real ID that Kash didn't know. Ask the user once.
        // Phone numbers never qualify: real banks don't send from them.
        if (imitates != null && imitates.isBank && !PHONE_LIKE.matches(rawSender.filter { !it.isWhitespace() })) {
            val parsed = banks.parse(imitates, rawSender, body, fallbackTime)
            if (parsed is ParseOutcome.Parsed && banks.matchesKnownFormat(imitates, body)) {
                return ParseOutcome.ConfirmSender(Sanitizer.displayName(rawSender) ?: rawSender, imitates, parsed.tx)
            }
        }
        val signals = buildList {
            if (imitates != null) add(ScamSignal.LOOKALIKE_SENDER)
            addAll(ScamDetector.signals(body))
        }
        val score = ScamDetector.score(signals)
        return if (score >= ScamDetector.ALERT_THRESHOLD) {
            ParseOutcome.ScamAlert(Sanitizer.displayName(rawSender) ?: "Unknown", score, signals, imitates)
        } else {
            ParseOutcome.Ignored(IgnoreReason.UNTRUSTED_SENDER)
        }
    }
}
