package com.kash.parser

sealed interface SenderClass {
    data class Trusted(val provider: Provider) : SenderClass
    /** An ordinary phone number. Real M-Pesa and bank messages never come from one. */
    data object PersonalNumber : SenderClass
    data object Unknown : SenderClass
}

/**
 * Layer 1 of the filter: who sent it.
 *
 * Safaricom and banks send from registered alphanumeric sender IDs, which ordinary
 * phones can't use. Scammers send from 07xx / 01xx numbers. This check alone removes
 * most fake confirmations.
 */
class SenderPolicy(private val trusted: Map<String, Provider>) {

    fun classify(rawSender: String): SenderClass {
        val sender = Sanitizer.normalizeSender(rawSender) ?: return SenderClass.Unknown
        trusted[sender]?.let { return SenderClass.Trusted(it) }
        return if (PHONE.matches(sender)) SenderClass.PersonalNumber else SenderClass.Unknown
    }

    /**
     * Unknown alphanumeric senders that look like a trusted one: "MPESA-KE", "M-PESA1", "C0-OPBANK".
     * Look-alike digits are folded (0→O, 1→I, 5→S…) before comparing.
     */
    fun lookalikeOf(rawSender: String): Provider? {
        val s = Sanitizer.normalizeSender(rawSender)?.let(::foldDigits) ?: return null
        if (trusted.containsKey(Sanitizer.normalizeSender(rawSender))) return null
        for ((id, provider) in trusted) {
            val t = foldDigits(id)
            val similar = when {
                s == t -> true
                t.length >= 4 && s.contains(t) -> true
                t.length == 3 && s.startsWith(t) -> true
                t.length >= 5 && editDistance(s, t) <= 1 -> true
                else -> false
            }
            if (similar) return provider
        }
        return null
    }

    companion object {
        private fun foldDigits(s: String) = s.map {
            when (it) { '0' -> 'O'; '1' -> 'I'; '3' -> 'E'; '4' -> 'A'; '5' -> 'S'; '8' -> 'B'; else -> it }
        }.joinToString("")

        private fun editDistance(a: String, b: String): Int {
            val d = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                var prev = d[0]; d[0] = i
                for (j in 1..b.length) {
                    val tmp = d[j]
                    d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                    prev = tmp
                }
            }
            return d[b.length]
        }

        private val PHONE = Regex("""^(?:\+?254|0)[17]\d{8}$""")

        /**
         * Sender IDs after normalisation (uppercase, letters and digits only).
         *
         * MPESA is confirmed. The bank IDs are best guesses and MUST be checked against
         * real messages before release; a wrong ID just means that bank's messages go
         * to the review queue, never that a fake is trusted.
         */
        val DEFAULT_TRUSTED: Map<String, Provider> = mapOf(
            "MPESA" to Provider.MPESA,
            "AIRTELMONEY" to Provider.AIRTEL_MONEY,
            "AIRTELMONEYKE" to Provider.AIRTEL_MONEY,
            "CITIBANK" to Provider.CITI,
            "CITIKENYA" to Provider.CITI,
            "CITI" to Provider.CITI,
            "STANBIC" to Provider.STANBIC,
            "STANBICBANK" to Provider.STANBIC,
            "EQUITYBANK" to Provider.EQUITY,
            "EQUITY" to Provider.EQUITY,
            "KCB" to Provider.KCB,
            "KCBGROUP" to Provider.KCB,
            "NCBA" to Provider.NCBA,
            "NCBABANK" to Provider.NCBA,
            "COOPBANK" to Provider.COOP,
            "ABSA" to Provider.ABSA,
            "ABSAKENYA" to Provider.ABSA,
            "STANCHART" to Provider.STANCHART,
            "FAMILYBANK" to Provider.FAMILY,
            "IMBANK" to Provider.IM,
            "DTBKENYA" to Provider.DTB,
        )

        fun default(): SenderPolicy = SenderPolicy(DEFAULT_TRUSTED)
    }
}
