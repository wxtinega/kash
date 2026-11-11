package com.kash.parser

import java.time.LocalDateTime
import java.time.ZoneId

/** Remembers which transaction codes have been recorded. Backed by the encrypted database in the app. */
interface SeenStore {
    /** Returns true if the code was new and has now been recorded. */
    fun markIfNew(code: String): Boolean
}

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
    private val zone: ZoneId = ZoneId.of("Africa/Nairobi"),
) {

    fun process(sms: RawSms): ParseOutcome {
        val body = Sanitizer.cleanBody(sms.body) ?: return ParseOutcome.Ignored(IgnoreReason.MALFORMED)
        val fallbackTime = LocalDateTime.ofInstant(sms.receivedAt, zone)

        val outcome = when (val sender = senders.classify(sms.sender)) {
            is SenderClass.Trusted -> when (sender.provider) {
                Provider.MPESA -> mpesa.parse(body, fallbackTime)
                else -> banks.parse(sender.provider, sms.sender, body, fallbackTime)
            }
            SenderClass.PersonalNumber, SenderClass.Unknown -> untrusted(sms.sender, body)
        }

        if (outcome is ParseOutcome.Parsed && !seen.markIfNew(outcome.tx.code)) {
            return ParseOutcome.Duplicate(outcome.tx.code)
        }
        return outcome
    }

    private fun untrusted(rawSender: String, body: String): ParseOutcome {
        val imitates = senders.lookalikeOf(rawSender)
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
