package com.kash.parser

import java.util.Locale

/**
 * An amount of Kenyan shillings stored as whole cents.
 *
 * Money is never held in floating point, so totals can't drift by a shilling.
 * All arithmetic is overflow-checked.
 */
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(Math.addExact(cents, other.cents))
    operator fun minus(other: Money): Money = Money(Math.subtractExact(cents, other.cents))
    operator fun unaryMinus(): Money = Money(Math.negateExact(cents))
    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    val isPositive: Boolean get() = cents > 0

    /** "KSh 1,500.00" / "-KSh 20.00" */
    fun format(): String {
        val abs = Math.abs(cents)
        val whole = String.format(Locale.US, "%,d", abs / 100)
        val frac = (abs % 100).toString().padStart(2, '0')
        return (if (cents < 0) "-" else "") + "KSh $whole.$frac"
    }

    override fun toString(): String = format()

    companion object {
        val ZERO = Money(0)

        /** Anything above KSh 1 billion in a single message is treated as malformed. */
        const val MAX_CENTS: Long = 100_000_000_000L

        private val NUMBER = Regex("""^(\d{1,3}(?:,\d{3})+|\d+)(?:\.(\d{1,2}))?$""")

        fun ofShillings(shillings: Long): Money = Money(Math.multiplyExact(shillings, 100L))

        /**
         * Parses the numeric part of an amount, e.g. "1,500.00", "250", "0.5".
         * Returns null for anything malformed or out of range. Never uses floating point.
         */
        fun parseNumber(raw: String): Money? {
            val m = NUMBER.matchEntire(raw.trim()) ?: return null
            val whole = m.groupValues[1].replace(",", "")
            if (whole.length > 10) return null
            val frac = m.groupValues[2].padEnd(2, '0')
            val cents = whole.toLong() * 100 + frac.toLong()
            return if (cents > MAX_CENTS) null else Money(cents)
        }
    }
}
