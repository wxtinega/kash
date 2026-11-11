package com.kash.parser

import java.time.Duration

/**
 * Layer 3 of the filter: every M-Pesa message states the new balance, so
 *   previous balance ± amount − fee  must equal  the new balance.
 *
 * A break means either a message is missing (deleted, phone was off) or one of them
 * is not genuine. The app shows breaks to the user rather than silently "fixing" them.
 */
object BalanceChain {

    data class Break(
        val tx: Transaction,
        val previous: Transaction,
        val expected: Money,
        val actual: Money,
    ) {
        val difference: Money get() = actual - expected
    }

    /** Fuliza credit counts as explaining a gap if it happened within this window. */
    private val FULIZA_WINDOW: Duration = Duration.ofMinutes(5)

    /**
     * @param txs transactions for ONE account (one provider), in any order.
     */
    fun check(txs: List<Transaction>): List<Break> {
        // Messages only show the minute; the phone's receive time orders two payments in the same minute.
        val ordered = txs.sortedWith(compareBy<Transaction> { it.occurredAt }.thenBy { it.receivedAt }.thenBy { it.code })
        val fulizaCredits = ordered.filter { it.type == TxType.FULIZA_LOAN }
        val breaks = mutableListOf<Break>()
        var prev: Transaction? = null

        for (tx in ordered) {
            val actual = tx.balanceAfter ?: continue // e.g. Fuliza notices: no balance stated
            val p = prev
            if (p != null && tx.type.flow != Flow.ADJUST) {
                val expected = p.balanceAfter!! + delta(tx)
                if (expected != actual && !explainedByFuliza(tx, actual - expected, fulizaCredits)) {
                    breaks += Break(tx, p, expected, actual)
                }
            }
            prev = tx // restart the chain from the stated balance either way
        }
        return breaks
    }

    fun delta(tx: Transaction): Money = when (tx.type.flow) {
        Flow.IN -> tx.amount
        Flow.OUT -> -(tx.amount + tx.fee)
        Flow.CREDIT_LINE, Flow.ADJUST -> Money.ZERO
    }

    private fun explainedByFuliza(tx: Transaction, gap: Money, credits: List<Transaction>): Boolean {
        val nearby = credits.filter {
            Duration.between(it.occurredAt, tx.occurredAt).abs() <= FULIZA_WINDOW
        }
        if (nearby.isEmpty()) return false
        val total = nearby.fold(Money.ZERO) { acc, c -> acc + c.amount }
        return gap == total
    }
}
