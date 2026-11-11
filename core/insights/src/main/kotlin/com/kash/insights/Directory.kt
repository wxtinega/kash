package com.kash.insights

/**
 * Built-in knowledge of common Kenyan businesses and name keywords.
 * Kept small and conservative: a wrong guess is worse than asking.
 */
internal object Directory {

    private val KNOWN: List<Pair<String, Category>> = listOf(
        // utilities & bills (phone data bundles and home internet count as bills)
        "SAFARICOM DATA BUNDLES" to Category.UTILITIES, "SAFARICOM DATA" to Category.UTILITIES,
        "SAFARICOM BUNDLES" to Category.UTILITIES, "SAFARICOM OFFERS" to Category.UTILITIES,
        "SAFARICOM POSTPAID" to Category.UTILITIES, "SAFARICOM FIBRE" to Category.UTILITIES,
        "AIRTEL BUNDLES" to Category.UTILITIES, "AIRTEL DATA" to Category.UTILITIES,
        "TELKOM" to Category.UTILITIES,
        "KPLC" to Category.UTILITIES, "KENYA POWER" to Category.UTILITIES, "NAIROBI WATER" to Category.UTILITIES,
        "ZUKU" to Category.UTILITIES, "SAFARICOM HOME" to Category.UTILITIES, "FAIBA" to Category.UTILITIES,
        "DSTV" to Category.SUBSCRIPTIONS, "GOTV" to Category.SUBSCRIPTIONS, "SHOWMAX" to Category.SUBSCRIPTIONS,
        "NETFLIX" to Category.SUBSCRIPTIONS, "SPOTIFY" to Category.SUBSCRIPTIONS, "YOUTUBE" to Category.SUBSCRIPTIONS,
        "ANTHROPIC" to Category.SUBSCRIPTIONS, "OPENAI" to Category.SUBSCRIPTIONS, "APPLE COM" to Category.SUBSCRIPTIONS,
        "GOOGLE" to Category.SUBSCRIPTIONS,
        // supermarkets
        "NAIVAS" to Category.SHOPPING, "QUICKMART" to Category.SHOPPING, "CARREFOUR" to Category.SHOPPING,
        "CHANDARANA" to Category.SHOPPING, "MAJID" to Category.SHOPPING, "CLEANSHELF" to Category.SHOPPING,
        // food
        "JAVA HOUSE" to Category.FOOD, "KFC" to Category.FOOD, "ARTCAFFE" to Category.FOOD, "PIZZA INN" to Category.FOOD,
        "CHICKEN INN" to Category.FOOD, "GALITOS" to Category.FOOD, "GLOVO" to Category.FOOD, "BOLT FOOD" to Category.FOOD,
        // transport & fuel
        "UBER" to Category.FUEL_TRANSPORT, "BOLT" to Category.FUEL_TRANSPORT,
        "TOTALENERGIES" to Category.FUEL_TRANSPORT, "RUBIS" to Category.FUEL_TRANSPORT, "SHELL" to Category.FUEL_TRANSPORT,
        "OLA ENERGY" to Category.FUEL_TRANSPORT, "VIVO ENERGY" to Category.FUEL_TRANSPORT,
        // betting
        "SPORTPESA" to Category.BETTING, "BETIKA" to Category.BETTING, "ODIBETS" to Category.BETTING,
        "MOZZART" to Category.BETTING, "SHABIKI" to Category.BETTING, "BETWAY" to Category.BETTING, "22BET" to Category.BETTING,
        // school, loans (savings and investment schemes live in SchemeRegistry)
        "HELB" to Category.SCHOOL,
        "TALA" to Category.LOAN_REPAYMENT, "BRANCH" to Category.LOAN_REPAYMENT, "ZASH" to Category.LOAN_REPAYMENT,
        "HUSTLER FUND" to Category.LOAN_REPAYMENT,
    )

    private val KEYWORDS: List<Pair<String, Category>> = listOf(
        "CAFE" to Category.FOOD, "RESTAURANT" to Category.FOOD, "HOTEL" to Category.FOOD, "EATERY" to Category.FOOD,
        "KITCHEN" to Category.FOOD, "BUTCHERY" to Category.FOOD, "BAKERY" to Category.FOOD, "FRIES" to Category.FOOD,
        "MAMA" to Category.FOOD, "CHIPS" to Category.FOOD,
        "PETROL" to Category.FUEL_TRANSPORT, "SERVICE STATION" to Category.FUEL_TRANSPORT, "FUEL" to Category.FUEL_TRANSPORT,
        "MATATU" to Category.FUEL_TRANSPORT,
        "WINE" to Category.GOING_OUT, "LIQUOR" to Category.GOING_OUT, "PUB" to Category.GOING_OUT, "LOUNGE" to Category.GOING_OUT,
        "BAR" to Category.GOING_OUT, "CLUB" to Category.GOING_OUT,
        "PHARMACY" to Category.HEALTH, "CHEMIST" to Category.HEALTH, "HOSPITAL" to Category.HEALTH, "CLINIC" to Category.HEALTH,
        "SCHOOL" to Category.SCHOOL, "UNIVERSITY" to Category.SCHOOL, "COLLEGE" to Category.SCHOOL, "BOOKSHOP" to Category.SCHOOL,
        "SUPERMARKET" to Category.SHOPPING, "MINIMART" to Category.SHOPPING, "SHOP" to Category.SHOPPING,
        "BET" to Category.BETTING,
        "RENTAL" to Category.RENT_HOUSING, "APARTMENTS" to Category.RENT_HOUSING, "HOSTEL" to Category.RENT_HOUSING,
    )

    private fun containsWord(name: String, word: String) =
        Regex("""(^|\s)${Regex.escape(word)}(\s|$)""").containsMatchIn(name)

    fun lookup(name: String): Pair<Category, String>? =
        KNOWN.firstOrNull { (k, _) -> containsWord(name, k) }?.let { it.second to it.first }

    fun keyword(name: String): Pair<Category, String>? =
        KEYWORDS.firstOrNull { (k, _) -> containsWord(name, k) }?.let { it.second to it.first }
}
