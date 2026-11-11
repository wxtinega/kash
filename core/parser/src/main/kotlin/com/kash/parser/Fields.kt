package com.kash.parser

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Small, strict extractors for the fields that appear across message types. */
internal object Fields {

    /** Currency + number. Exposes the number as group "amt". */
    const val AMT = """(?:Ksh|KES|Kshs)\.?\s?(?<amt>(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?)"""

    private const val NUM = """((?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?)"""
    private const val CUR = """(?:Ksh|KES|Kshs)\.?\s?"""

    /** Real M-Pesa codes: 10 uppercase letters/digits, starting with a letter, followed by "Confirmed". */
    private val MPESA_CODE = Regex("""^([A-Z][A-Z0-9]{9})\s*\.?\s*(?i:confirmed)""")
    val CODE_ANYWHERE = Regex("""\b[A-Z][A-Z0-9]{9}\b""")

    // Currency is optional: real Fuliza repayments say "Your M-PESA balance is 699.34."
    private val BALANCE = listOf(
        Regex("""M-PESA balance is\s*(?:$CUR)?$NUM""", RegexOption.IGNORE_CASE),
        Regex("""account balance is now\s*(?:$CUR)?$NUM""", RegexOption.IGNORE_CASE),
    )
    private val FEE = Regex("""Transaction cost,?\s*$CUR$NUM""", RegexOption.IGNORE_CASE)
    private val FULIZA_FEE = Regex("""Access Fee charged\s*$CUR$NUM""", RegexOption.IGNORE_CASE)
    private val FULIZA_OUTSTANDING = Regex("""outstanding amount is\s*$CUR$NUM""", RegexOption.IGNORE_CASE)

    private val DATE_TIME = Regex(
        """on\s*(\d{1,2}/\d{1,2}/(?:\d{4}|\d{2}))\s*at\s*(\d{1,2}:\d{2})\s*([AP]M)""",
        RegexOption.IGNORE_CASE,
    )
    private val FMT_SHORT = DateTimeFormatter.ofPattern("d/M/yy h:mm a", Locale.ENGLISH)
    private val FMT_LONG = DateTimeFormatter.ofPattern("d/M/yyyy h:mm a", Locale.ENGLISH)

    private val TRAILING_PHONE = Regex("""[\s-]+((?:\+?254|0)[\d*]{9,10})$""")

    fun mpesaCode(body: String): String? = MPESA_CODE.find(body)?.groupValues?.get(1)

    fun balance(body: String): Money? =
        BALANCE.firstNotNullOfOrNull { it.find(body) }?.let { Money.parseNumber(it.groupValues[1]) }

    fun fee(body: String): Money? = FEE.find(body)?.let { Money.parseNumber(it.groupValues[1]) }
    fun fulizaFee(body: String): Money? = FULIZA_FEE.find(body)?.let { Money.parseNumber(it.groupValues[1]) }
    fun fulizaOutstanding(body: String): Money? =
        FULIZA_OUTSTANDING.find(body)?.let { Money.parseNumber(it.groupValues[1]) }

    fun dateTime(body: String): LocalDateTime? {
        val m = DATE_TIME.find(body) ?: return null
        val (date, time, ampm) = m.destructured
        val text = "$date $time ${ampm.uppercase()}"
        val fmt = if (date.substringAfterLast('/').length == 4) FMT_LONG else FMT_SHORT
        return try {
            LocalDateTime.parse(text, fmt)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    // Bank date styles seen in real messages:
    //   07-11-2026 04:38 PM | 20-Oct-2026 12:46:42 | 09/02/2026 at 16:31:49
    private val BANK_DATE_TIME = Regex(
        """(\d{1,2})[-/](\d{1,2}|[A-Za-z]{3})[-/](\d{4})(?:\s*at)?\s+(\d{1,2}):(\d{2})(?::(\d{2}))?\s*([AP]M)?""",
        RegexOption.IGNORE_CASE,
    )
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    fun bankDateTime(body: String): LocalDateTime? {
        val m = BANK_DATE_TIME.find(body) ?: return null
        val g = m.groupValues
        val day = g[1].toInt()
        val month = g[2].toIntOrNull() ?: (MONTHS.indexOf(g[2].lowercase()) + 1).takeIf { it > 0 } ?: return null
        var hour = g[4].toInt()
        val minute = g[5].toInt()
        val second = g[6].toIntOrNull() ?: 0
        when (g[7].uppercase()) {
            "PM" -> if (hour in 1..11) hour += 12
            "AM" -> if (hour == 12) hour = 0
        }
        return try {
            LocalDateTime.of(g[3].toInt(), month, day, hour, minute, second)
        } catch (e: java.time.DateTimeException) {
            null
        }
    }

    /** "JOHN DOE 0712345678" -> ("JOHN DOE", "0712345678"). */
    fun splitNameAndPhone(raw: String): Pair<String?, String?> {
        val m = TRAILING_PHONE.find(raw.trim()) ?: return Sanitizer.displayName(raw) to null
        val name = raw.trim().substring(0, m.range.first)
        return Sanitizer.displayName(name) to m.groupValues[1]
    }
}
