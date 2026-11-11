package com.kash.insights

/**
 * Savings and investment schemes Kenyans commonly use, and how each one behaves.
 *
 * Identification is by NAME, because an M-Pesa paybill confirmation shows the business name
 * ("sent to BRITAM ASSET MANAGERS for account …"), not the paybill number.
 *
 * No yields are stored here: they change daily. Kash learns each holding's real growth from
 * the user's check-ins and the fund's own messages (see [InvestmentTracker]).
 *
 * Research notes and sources: docs/investments.md
 */
enum class SchemeKind(val label: String) {
    MONEY_MARKET_FUND("Money market fund"),
    SPECIAL_FUND("Special / fixed income fund"),
    MOBILE_SAVINGS("Mobile savings"),
    LOCKED_SAVINGS("Locked savings"),
    SACCO("SACCO"),
    GOVERNMENT_SECURITIES("Treasury bills & bonds"),
    SHARES("Shares"),
    GOVERNMENT_SAVINGS("Government savings scheme"),
    PENSION("Pension"),
    CHAMA("Chama"),
}

/** How value grows, which decides how Kash estimates it and how often it asks. */
enum class Growth {
    /** Interest accrues daily, credited monthly after 15% withholding tax (MMFs, M-Shwari). */
    DAILY_ACCRUAL,
    /** Dividends on shares + interest on deposits, declared yearly at the AGM (SACCOs). */
    ANNUAL_DIVIDEND,
    /** Bought at a discount, full face value paid at maturity (T-bills). */
    PAID_AT_MATURITY,
    /** Moves with the market (shares, some special funds). */
    MARKET_PRICE,
    /** Only contributions; value comes from statements (pension, chama). */
    CONTRIBUTIONS_ONLY,
}

/** How often to ask "What's your balance today?". */
enum class CheckIn(val months: Int?) { MONTHLY(1), QUARTERLY(3), AT_MATURITY(null), NEVER(null) }

data class Scheme(
    val id: String,
    val name: String,
    val kind: SchemeKind,
    val growth: Growth,
    /** Phrases as they appear in M-Pesa / bank messages, already uppercase. Any one is a strong match. */
    val namePhrases: List<String>,
    val checkIn: CheckIn,
    /** Typical time to get money back, for the UI ("usually 1–3 working days"). */
    val liquidity: String,
    /** Tax withheld on returns, in basis points (1500 = 15%). Null = varies / not withheld. */
    val withholdingBps: Int?,
    val notes: String = "",
)

object SchemeRegistry {

    private fun mmf(id: String, name: String, vararg phrases: String, liquidity: String = "Usually 1–3 working days") =
        Scheme(id, name, SchemeKind.MONEY_MARKET_FUND, Growth.DAILY_ACCRUAL, phrases.toList(), CheckIn.MONTHLY, liquidity, 1500)

    val ALL: List<Scheme> = listOf(
        // ---- Money market funds (CMA-regulated unit trusts). Interest daily, credited monthly, 15% WHT. ----
        mmf("ziidi", "Ziidi Money Market Fund", "ZIIDI", liquidity = "Instant, straight to M-Pesa"),
        mmf("sanlam", "SanlamAllianz Money Market Fund", "SANLAM", "SANLAMALLIANZ"),
        mmf("cic", "CIC Money Market Fund", "CIC ASSET", "CIC MONEY MARKET", "CIC UNIT TRUST"),
        mmf("britam", "Britam Money Market Fund", "BRITAM ASSET", "BRITAM MONEY MARKET", "BRITAM UNIT TRUST"),
        mmf("etica", "Etica Money Market Fund", "ETICA"),
        mmf("cytonn", "Cytonn Money Market Fund", "CYTONN"),
        mmf("madison", "Madison Money Market Fund", "MADISON ASSET", "MADISON MONEY MARKET", "MADISON UNIT TRUST"),
        mmf("oldmutual", "Old Mutual Money Market Fund", "OLD MUTUAL ASSET", "OLD MUTUAL UNIT TRUST", "OLD MUTUAL MONEY MARKET"),
        mmf("icealion", "ICEA Lion Money Market Fund", "ICEA LION ASSET", "ICEA LION MONEY MARKET", "ICEA LION UNIT TRUST"),
        mmf("jubilee", "Jubilee Money Market Fund", "JUBILEE ASSET", "JUBILEE MONEY MARKET", "JUBILEE UNIT TRUST"),
        mmf("kuza", "Kuza Money Market Fund", "KUZA"),
        mmf("lofty", "Lofty-Corban Money Market Fund", "LOFTY", "CORBAN"),
        mmf("nabo", "Nabo Africa Money Market Fund", "NABO"),
        mmf("zimele", "Zimele Money Market Fund", "ZIMELE"),
        mmf("gencap", "GenCap Hela Imara Money Market Fund", "HELA IMARA", "GENCAP"),
        mmf("kasha", "Orient Kasha Money Market Fund", "ORIENT KASHA", "KASHA MONEY MARKET"),
        mmf("ndovu", "Ndovu Money Market Fund", "NDOVU"),
        mmf("enwealth", "Enwealth Money Market Fund", "ENWEALTH"),
        mmf("dry", "Dry Associates Money Market Fund", "DRY ASSOCIATES"),
        mmf("apollo", "Apollo Money Market Fund", "APOLLO ASSET", "APOLLO MONEY MARKET"),
        mmf("aa", "African Alliance Money Market Fund", "AFRICAN ALLIANCE"),
        mmf("kcbmmf", "KCB Money Market Fund", "KCB ASSET", "KCB MONEY MARKET", "KCB INVESTMENT"),
        mmf("ncbammf", "NCBA Money Market Fund", "NCBA ASSET", "NCBA MONEY MARKET", "NCBA INVESTMENT"),
        mmf("coopmmf", "Co-op Money Market Fund", "COOP TRUST", "CO OP TRUST", "COOP MONEY MARKET"),
        mmf("absammf", "Absa Money Market Fund", "ABSA ASSET", "ABSA MONEY MARKET", "ABSA UNIT TRUST"),
        mmf("stanbicmmf", "Stanbic Money Market Fund", "STANBIC MONEY MARKET", "SBG SECURITIES"),
        mmf("equitymmf", "Equity Money Market Fund", "EQUITY INVESTMENT", "EQUITY MONEY MARKET"),
        mmf("mali", "Mali (Safaricom, older fund)", "GENGHIS", "MALI MONEY MARKET"),

        // ---- Special / fixed-income funds: priced daily, can move both ways. ----
        Scheme(
            "mansax", "Mansa-X (Standard Investment Trust)", SchemeKind.SPECIAL_FUND, Growth.MARKET_PRICE,
            listOf("MANSA", "STANDARD INVESTMENT"), CheckIn.MONTHLY, "Varies by fund", 1500,
            "Special funds can lose value in a month; Kash shows real changes, never assumes growth.",
        ),

        // ---- Mobile savings inside M-Pesa ----
        Scheme(
            "mshwari", "M-Shwari", SchemeKind.MOBILE_SAVINGS, Growth.DAILY_ACCRUAL,
            listOf("M SHWARI", "MSHWARI"), CheckIn.MONTHLY, "Instant", null,
            "Detected from M-Pesa 'transferred to M-Shwari' messages, which also state the M-Shwari balance.",
        ),
        Scheme(
            "mshwarilock", "M-Shwari Lock Savings", SchemeKind.LOCKED_SAVINGS, Growth.DAILY_ACCRUAL,
            listOf("LOCK SAVINGS"), CheckIn.AT_MATURITY, "Locked until the date you chose", null,
            "Interest earned daily, paid monthly into the lock account.",
        ),
        Scheme(
            "kcbmpesa", "KCB M-PESA Savings", SchemeKind.MOBILE_SAVINGS, Growth.DAILY_ACCRUAL,
            listOf("KCB M PESA", "KCB MPESA"), CheckIn.MONTHLY, "Instant",
            null, "Fixed savings: withdrawing before the end date forfeits the interest.",
        ),

        // ---- Government ----
        Scheme(
            "tbills", "Treasury bills & bonds (DhowCSD)", SchemeKind.GOVERNMENT_SECURITIES, Growth.PAID_AT_MATURITY,
            listOf("DHOWCSD", "DHOW CSD", "CENTRAL BANK OF KENYA", "CBK"), CheckIn.AT_MATURITY,
            "At maturity (91/182/364 days), or sell early on the secondary market", 0,
            "Minimum KSh 100,000 for T-bills (KSh 50,000 for bonds). Paid by M-Pesa via DhowCSD.",
        ),
        Scheme(
            "hustler", "Hustler Fund savings", SchemeKind.GOVERNMENT_SAVINGS, Growth.CONTRIBUTIONS_ONLY,
            listOf("HUSTLER FUND SAVINGS"), CheckIn.QUARTERLY,
            "Short-term part: anytime with no loan outstanding. Long-term part: at 60 (30% every 5 years)", null,
            "5% of every Hustler loan is saved automatically: 30% short-term, 70% long-term.",
        ),

        // ---- SACCOs ----
        Scheme(
            "sacco", "SACCO", SchemeKind.SACCO, Growth.ANNUAL_DIVIDEND,
            listOf("SACCO"), CheckIn.QUARTERLY,
            "Deposits: usually only on leaving (60–90 days' notice). Share capital: not withdrawable", 500,
            "Dividends on shares and interest on deposits are declared at the AGM, typically paid March–June.",
        ),

        // ---- Shares ----
        Scheme(
            "shares", "Shares (NSE)", SchemeKind.SHARES, Growth.MARKET_PRICE,
            listOf("ZIIDI TRADER", "CDSC", "FAIDA INVESTMENT", "DYER AND BLAIR", "AIB AXYS", "HISA"), CheckIn.MONTHLY,
            "Sale proceeds usually within a few days (Ziidi Trader: same day)", null,
        ),
    )

    /** Names that suggest saving/investing but also match ordinary shops ("CHRON INVESTMENTS"). Ask, don't assume. */
    private val WEAK = listOf("INVESTMENT", "INVESTMENTS", "CAPITAL", "ASSET", "ASSETS", "WEALTH", "TRUST", "SECURITIES", "FUND")

    /** Generic phrases that are clearly a fund even when the manager isn't in the list. */
    private val GENERIC_FUND = listOf("MONEY MARKET", "UNIT TRUST", "MMF")

    sealed interface Match {
        data class Strong(val scheme: Scheme, val phrase: String) : Match
        data class GenericFund(val phrase: String) : Match
        data class Weak(val word: String) : Match
    }

    private fun has(name: String, phrase: String) = Regex("""(^|\s)${Regex.escape(phrase)}(\s|$)""").containsMatchIn(name)

    /** [name] should already be normalised with [MerchantKey.normalizeName]. */
    fun match(name: String): Match? {
        if (name.isBlank()) return null
        // Longest phrase first, so "ZIIDI TRADER" (shares) beats "ZIIDI" (fund).
        val candidates = ALL.flatMap { s -> s.namePhrases.map { s to it } }.sortedByDescending { it.second.length }
        candidates.firstOrNull { (_, p) -> has(name, p) }?.let { (s, p) -> return Match.Strong(s, p) }
        GENERIC_FUND.firstOrNull { has(name, it) }?.let { return Match.GenericFund(it) }
        WEAK.firstOrNull { has(name, it) }?.let { return Match.Weak(it) }
        return null
    }

    fun byId(id: String): Scheme? = ALL.firstOrNull { it.id == id }
}
