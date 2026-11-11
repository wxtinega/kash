package com.kash.insights

import com.kash.parser.Transaction
import com.kash.parser.TxType

/**
 * A stable identity for "who the money went to / came from", so an answer given once
 * applies to every future payment to the same place.
 *
 *  - People are identified by phone number when the message has an unmasked one
 *    (names repeat; numbers don't), otherwise by name.
 *  - Paybills by business name AND account number: "KPLC PREPAID" is always power, but the
 *    same bank paybill can be rent under one account and school fees under another.
 *  - Tills and card merchants by name, with branch noise removed.
 */
@JvmInline
value class MerchantKey(val value: String) {
    /** The less specific key to fall back on (paybill name without the account). */
    val parent: MerchantKey? get() = value.substringBeforeLast('|', "").takeIf { it.isNotEmpty() }?.let(::MerchantKey)

    companion object {
        private val NOISE = Regex("""\b(LTD|LIMITED|ENTERPRISES?|AGENCIES|CO|KENYA|KE)\b\.?""")
        private val SPACES = Regex("""\s+""")

        fun normalizeName(name: String): String =
            name.uppercase().replace(NOISE, " ").replace(Regex("""[^A-Z0-9& ]"""), " ").replace(SPACES, " ").trim()

        private fun phoneKey(phone: String?): String? {
            if (phone == null || phone.contains('*')) return null
            val digits = phone.filter(Char::isDigit)
            return when {
                digits.startsWith("254") && digits.length == 12 -> "0" + digits.substring(3)
                digits.length == 10 -> digits
                else -> null
            }
        }

        fun of(tx: Transaction): MerchantKey? {
            val name = tx.counterparty?.let(::normalizeName)?.takeIf { it.isNotEmpty() }
            return when (tx.type) {
                TxType.SENT, TxType.RECEIVED ->
                    (phoneKey(tx.counterpartyPhone)?.let { "person:$it" } ?: name?.let { "person:$it" })?.let(::MerchantKey)
                TxType.PAYBILL -> name?.let {
                    val acct = tx.accountNumber
                    MerchantKey(if (acct != null) "paybill:$it|${normalizeName(acct)}" else "paybill:$it")
                }
                TxType.TILL -> name?.let { MerchantKey("till:$it") }
                TxType.BANK_DEBIT, TxType.BANK_CREDIT -> name?.let { MerchantKey("bank:${tx.provider.name}:$it") }
                else -> null // airtime, withdrawals, Fuliza, M-Shwari: the type alone decides
            }
        }
    }
}
