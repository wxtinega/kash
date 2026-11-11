package com.kash.insights

import com.kash.parser.Money
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Decides whether a "What was this?" question goes out as a push notification now,
 * or waits quietly in the app's review list. Keeps the app helpful without nagging,
 * which is the main reason people abandon budgeting apps.
 */
class QuestionPolicy(
    val maxPushesPerDay: Int = 3,
    val quietStart: LocalTime = LocalTime.of(21, 30),
    val quietEnd: LocalTime = LocalTime.of(7, 30),
    /** Small amounts never trigger a push; they wait in the list. */
    val minimumForPush: Money = Money.ofShillings(50),
) {
    enum class Decision { PUSH_NOW, HOLD_UNTIL_MORNING, LIST_ONLY }

    fun decide(amount: Money, at: LocalDateTime, pushesSentToday: Int): Decision {
        if (amount < minimumForPush || pushesSentToday >= maxPushesPerDay) return Decision.LIST_ONLY
        val t = at.toLocalTime()
        val quiet = if (quietStart > quietEnd) t >= quietStart || t < quietEnd else t in quietStart..quietEnd
        return if (quiet) Decision.HOLD_UNTIL_MORNING else Decision.PUSH_NOW
    }
}
