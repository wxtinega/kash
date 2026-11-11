package com.kash.insights

import com.kash.parser.Transaction
import com.kash.parser.TxType

/** The user's own answers, stored in the encrypted database in the app. */
interface LearnedRules {
    fun get(key: MerchantKey): Category?
    fun put(key: MerchantKey, category: Category)
}

class InMemoryLearnedRules : LearnedRules {
    private val map = HashMap<MerchantKey, Category>()
    @Synchronized override fun get(key: MerchantKey) = map[key]
    @Synchronized override fun put(key: MerchantKey, category: Category) { map[key] = category }
}

/** What the app does with a transaction it can't place. */
sealed interface Placement {
    data class Known(val result: Categorized) : Placement
    /**
     * Ask the user. [choices] are the quick buttons for the notification (Android shows up to 3):
     * two likely answers plus "Something else". Answering dismisses the notification.
     */
    data class Ask(val key: MerchantKey?, val prompt: String, val choices: List<Category>) : Placement
}

/**
 * Order of evidence, strongest first:
 *  1. linked transfer between the user's own accounts
 *  2. the user's own earlier answer for this merchant/person
 *  3. the transaction type (airtime, withdrawal, Fuliza, M-Shwari…)
 *  4. built-in directory of known Kenyan businesses
 *  5. keywords in the name ("CAFE", "PHARMACY", "PETROL"…)
 *  6. otherwise: ask
 */
class Categorizer(
    private val learned: LearnedRules = InMemoryLearnedRules(),
    /** M-Pesa codes that a bank message also mentions (the two halves of one transfer). */
    private val linkedCodes: () -> Set<String> = { emptySet() },
    private val isOwnAccount: (String) -> Boolean = { false },
) {

    fun place(tx: Transaction): Placement {
        transferEvidence(tx)?.let { return Placement.Known(Categorized(Category.TRANSFER, Source.LINKED_TRANSFER, it)) }

        val key = MerchantKey.of(tx)
        if (key != null) {
            (learned.get(key) ?: key.parent?.let(learned::get))?.let {
                return Placement.Known(Categorized(it, Source.LEARNED, "You marked ${tx.counterparty} as ${it.label}"))
            }
        }

        schemeEvidence(tx)?.let { return it }

        byType(tx)?.let { return Placement.Known(it) }

        // Business directory and keywords only for businesses, never for people.
        val isBusiness = tx.type in setOf(TxType.TILL, TxType.PAYBILL, TxType.BANK_DEBIT)
        val name = if (isBusiness) tx.counterparty?.let(MerchantKey::normalizeName).orEmpty() else ""
        if (name.isNotEmpty()) Directory.lookup(name)?.let { (cat, matched) ->
            return Placement.Known(Categorized(cat, Source.BUILT_IN, "$matched is a known ${cat.label.lowercase()} business"))
        }
        if (name.isNotEmpty()) Directory.keyword(name)?.let { (cat, word) ->
            return Placement.Known(Categorized(cat, Source.KEYWORD, "The name contains \"$word\""))
        }

        return ask(tx, key)
    }

    /** Called when the user taps an answer. Applies to every future payment to the same place. */
    fun learn(key: MerchantKey, category: Category) = learned.put(key, category)

    /**
     * Payments to a known fund/SACCO/etc. are savings, not spending; money back from one is a
     * withdrawal from savings, not income. Ambiguous names ("CHRON INVESTMENTS") are asked about.
     */
    private fun schemeEvidence(tx: Transaction): Placement? {
        val types = setOf(TxType.TILL, TxType.PAYBILL, TxType.BANK_DEBIT, TxType.RECEIVED, TxType.BANK_CREDIT)
        if (tx.type !in types) return null
        if (tx.counterpartyPhone != null) return null // a person (surnames like NDOVU), not a business
        val name = tx.counterparty?.let(MerchantKey::normalizeName) ?: return null
        val incoming = tx.type == TxType.RECEIVED || tx.type == TxType.BANK_CREDIT
        return when (val m = SchemeRegistry.match(name)) {
            is SchemeRegistry.Match.Strong -> Placement.Known(
                Categorized(
                    Category.SAVINGS, Source.SCHEME,
                    if (incoming) "Withdrawal from your ${m.scheme.name}" else "Saved into ${m.scheme.name}",
                    m.scheme.id,
                ),
            )
            is SchemeRegistry.Match.GenericFund -> Placement.Known(
                Categorized(Category.SAVINGS, Source.SCHEME, "The name contains \"${m.phrase}\"", null),
            )
            // Weak words only from KSh 100 up (the lowest fund top-ups): a KSh 80 "CHRON INVESTMENTS" is a shop.
            is SchemeRegistry.Match.Weak -> if (incoming || tx.amount < MIN_WEAK_INVESTMENT) null else Placement.Ask(
                MerchantKey.of(tx),
                "${tx.amount.format()} paid to ${tx.counterparty}. Was this a saving or investment?",
                listOf(Category.SAVINGS, Category.SHOPPING, Category.OTHER),
            )
            null -> null
        }
    }

    private companion object {
        val MIN_WEAK_INVESTMENT: com.kash.parser.Money = com.kash.parser.Money.ofShillings(100)
    }

    private fun transferEvidence(tx: Transaction): String? = when {
        tx.code in linkedCodes() -> "Your bank confirmed this went to your own account"
        tx.linkedCode != null && tx.type in setOf(TxType.BANK_CREDIT, TxType.BANK_DEBIT) &&
            (tx.counterparty == "From your M-Pesa" || tx.counterparty == "To your M-Pesa" || tx.linkedCode in linkedCodes()) ->
            "Moved between your M-Pesa and your bank"
        tx.type == TxType.PAYBILL && tx.accountNumber != null && isOwnAccount(tx.accountNumber!!) ->
            "Paid into your own account ${tx.accountNumber}"
        else -> null
    }

    private fun byType(tx: Transaction): Categorized? {
        fun t(c: Category, why: String) = Categorized(c, Source.TRANSACTION_TYPE, why)
        return when (tx.type) {
            TxType.AIRTIME -> t(Category.AIRTIME_DATA, "Airtime purchase")
            TxType.WITHDRAW -> t(Category.CASH, "Cash withdrawal at an agent")
            TxType.DEPOSIT -> t(Category.MONEY_IN, "Cash deposit at an agent")
            TxType.MSHWARI_SAVE, TxType.MSHWARI_WITHDRAW -> t(Category.SAVINGS, "M-Shwari savings")
            TxType.FULIZA_LOAN -> t(Category.FEES, "Fuliza overdraft")
            TxType.FULIZA_REPAY -> t(Category.LOAN_REPAYMENT, "Fuliza repayment")
            TxType.REVERSAL -> t(Category.TRANSFER, "Reversal of an earlier transaction")
            TxType.RECEIVED, TxType.BANK_CREDIT -> t(Category.MONEY_IN, "Money received")
            else -> null
        }
    }

    private fun ask(tx: Transaction, key: MerchantKey?): Placement.Ask {
        val who = tx.counterparty ?: "this payment"
        val amount = tx.amount.format()
        val (prompt, likely) = when (tx.type) {
            TxType.SENT -> "You sent $amount to $who. What was it for?" to listOf(Category.FAMILY, Category.FRIENDS)
            TxType.TILL -> "$amount paid to $who. What was it?" to listOf(Category.FOOD, Category.FUEL_TRANSPORT)
            TxType.PAYBILL -> "$amount paid to $who. What was it?" to listOf(Category.UTILITIES, Category.SHOPPING)
            TxType.BANK_DEBIT -> "$amount from your bank to $who. What was it?" to listOf(Category.SHOPPING, Category.SUBSCRIPTIONS)
            else -> "$amount to $who. What was it?" to listOf(Category.FOOD, Category.SHOPPING)
        }
        return Placement.Ask(key, prompt, likely + Category.OTHER)
    }
}
