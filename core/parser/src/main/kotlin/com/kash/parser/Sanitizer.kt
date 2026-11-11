package com.kash.parser

import java.text.Normalizer

/**
 * First line of defence: every SMS is treated as hostile input.
 * Nothing in a message is ever executed, interpolated into SQL, or rendered as markup.
 */
object Sanitizer {

    /** A long concatenated SMS is ~1,000 characters; anything far beyond that is not a real confirmation. */
    const val MAX_BODY_LENGTH = 2_000
    const val MAX_SENDER_LENGTH = 20
    const val MAX_NAME_LENGTH = 80

    private val CONTROL = Regex("""[\p{Cc}\p{Cf}&&[^\n\t]]""")
    private val WHITESPACE = Regex("""\s+""")
    private val UNSAFE_NAME_CHARS = Regex("""[^\p{L}\p{N} .&'\-/()*]""")

    /**
     * Normalises a message body for parsing. NFKC folds look-alike characters such as
     * full-width digits ("１,０００") into plain ones, so tricks like that can't slip past the rules.
     * Returns null if the message is empty or too long.
     */
    fun cleanBody(raw: String): String? {
        if (raw.isBlank() || raw.length > MAX_BODY_LENGTH) return null
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        return normalized.replace(CONTROL, "").replace(WHITESPACE, " ").trim().ifEmpty { null }
    }

    /** "M-PESA", " mpesa " -> "MPESA". */
    fun normalizeSender(raw: String): String? {
        val s = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .uppercase()
            .filter { it.isLetterOrDigit() || it == '+' }
        return if (s.isEmpty() || s.length > MAX_SENDER_LENGTH) null else s
    }

    /** Makes a counterparty name safe to show anywhere (UI, PDF, CSV export). */
    fun displayName(raw: String?): String? {
        if (raw == null) return null
        val cleaned = raw.replace(UNSAFE_NAME_CHARS, "").replace(WHITESPACE, " ").trim().trimEnd('.')
        return cleaned.take(MAX_NAME_LENGTH).ifEmpty { null }
    }
}
