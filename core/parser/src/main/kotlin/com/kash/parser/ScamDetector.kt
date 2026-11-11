package com.kash.parser

/**
 * Scores messages from untrusted senders. A high score becomes a user-facing warning
 * ("This claims to be from M-Pesa but came from 0712…"); nothing from an untrusted
 * sender is ever recorded as money.
 */
object ScamDetector {

    const val ALERT_THRESHOLD = 50

    private val LOOKS_LIKE_CONFIRMATION = listOf(
        Regex("""\b[A-Z][A-Z0-9]{9}\s*\.?\s*confirmed""", RegexOption.IGNORE_CASE),
        Regex("""M-?PESA balance""", RegexOption.IGNORE_CASE),
        Regex("""You have received\s*(?:Ksh|KES)""", RegexOption.IGNORE_CASE),
        Regex("""Transaction cost""", RegexOption.IGNORE_CASE),
    )

    private val WRONG_TRANSFER = Regex(
        """sent (?:it |you |the money )?(?:by mistake|wrongly)|wrong(?:ly)? (?:number|sent|transfer)|by mistake|""" +
            """kindly (?:reverse|return|send (?:it )?back)|send (?:it |the money )?back|return (?:my|the) money|""" +
            """nimekosea|nimetuma kimakosa|kimakosa|rudisha|tuma (?:tena|back)|nirudishie""",
        RegexOption.IGNORE_CASE,
    )
    private val PIN = Regex("""\b(?:M-?PESA )?PIN\b|secret code|verification code to|share (?:the|your) code""", RegexOption.IGNORE_CASE)
    private val PRIZE = Regex(
        """you have won|congratulations|umeshinda|claim your|winner|promotion|bonus of|urgent|immediately|within \d+ (?:hours|minutes)|account (?:will be )?(?:blocked|suspended)""",
        RegexOption.IGNORE_CASE,
    )
    private val LOAN = Regex(
        """\bloan\b.{0,40}\b(?:limit|qualif|approved|offer|instant|up to|apply)|(?:qualif\w*|eligible) for (?:a )?loan|borrow up to|get (?:a |an instant )?loan|mkopo""",
        RegexOption.IGNORE_CASE,
    )
    private val FEE_FIRST = Regex(
        """(?:processing|registration|activation|insurance|clearance|service) fee|pay\s*(?:Ksh|KES)?\s*\d+.{0,40}(?:to (?:receive|get|unlock|activate|claim))""",
        RegexOption.IGNORE_CASE,
    )
    private val LINK = Regex("""https?://|www\.|bit\.ly|\b[a-z0-9-]+\.(?:com|net|xyz|top|info|co\.ke|ke)\b""", RegexOption.IGNORE_CASE)
    private val CALL = Regex("""\b(?:call|text|sms|piga|whatsapp)\b.{0,25}(?:\+?254|0)[17]\d{8}""", RegexOption.IGNORE_CASE)

    fun signals(body: String): List<ScamSignal> = buildList {
        if (LOOKS_LIKE_CONFIRMATION.count { it.containsMatchIn(body) } >= 1 && Fields.CODE_ANYWHERE.containsMatchIn(body)) {
            add(ScamSignal.IMITATES_PROVIDER)
        }
        if (WRONG_TRANSFER.containsMatchIn(body)) add(ScamSignal.WRONG_TRANSFER_STORY)
        if (PIN.containsMatchIn(body)) add(ScamSignal.ASKS_FOR_PIN)
        if (PRIZE.containsMatchIn(body)) add(ScamSignal.PRIZE_OR_URGENCY)
        if (LOAN.containsMatchIn(body)) add(ScamSignal.LOAN_BAIT)
        if (FEE_FIRST.containsMatchIn(body)) add(ScamSignal.UPFRONT_FEE)
        if (LINK.containsMatchIn(body)) add(ScamSignal.CONTAINS_LINK)
        if (CALL.containsMatchIn(body)) add(ScamSignal.ASKS_TO_CALL)
    }

    fun score(signals: List<ScamSignal>): Int = signals.sumOf { it.weight }.coerceAtMost(100)
}
