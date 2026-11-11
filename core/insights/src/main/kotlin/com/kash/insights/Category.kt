package com.kash.insights

enum class Category(val label: String, val isSpending: Boolean = true) {
    FOOD("Food"),
    FUEL_TRANSPORT("Fuel & transport"),
    AIRTIME_DATA("Airtime & data"),
    RENT_HOUSING("Rent & housing"),
    UTILITIES("Bills & utilities"),
    SCHOOL("School"),
    SHOPPING("Shopping"),
    GOING_OUT("Going out"),
    BETTING("Betting"),
    HEALTH("Health"),
    FAMILY("Family"),
    FRIENDS("Friends"),
    SUBSCRIPTIONS("Subscriptions"),
    CASH("Cash withdrawal"),
    FEES("Fees & Fuliza charges"),
    LOAN_REPAYMENT("Loan repayment"),
    SAVINGS("Savings & investments", isSpending = false),
    TRANSFER("Between your accounts", isSpending = false),
    MONEY_IN("Money in", isSpending = false),
    OTHER("Other"),
}

/** Where a category came from; shown when the user taps "Why is this Food?". */
enum class Source { LEARNED, BUILT_IN, KEYWORD, TRANSACTION_TYPE, LINKED_TRANSFER, SCHEME }

data class Categorized(
    val category: Category,
    val source: Source,
    val reason: String,
    /** Set when the payment is a contribution to / withdrawal from a known scheme (see [SchemeRegistry]). */
    val schemeId: String? = null,
)
