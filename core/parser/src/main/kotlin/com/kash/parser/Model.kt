package com.kash.parser

import java.time.Instant
import java.time.LocalDateTime

/** Version of the parsing rules. Stored with every transaction so we know which rules read it. */
const val PARSER_VERSION = 1

/** A message exactly as the phone received it. Treated as hostile input. */
data class RawSms(
    val sender: String,
    val body: String,
    val receivedAt: Instant,
)

enum class Provider(val displayName: String) {
    MPESA("M-Pesa"),
    EQUITY("Equity Bank"),
    KCB("KCB"),
    NCBA("NCBA"),
    COOP("Co-op Bank"),
    ABSA("Absa"),
    STANCHART("Standard Chartered"),
    FAMILY("Family Bank"),
    IM("I&M Bank"),
    DTB("DTB"),
    ;

    val isBank: Boolean get() = this != MPESA
}

/** How a transaction moves money in the account the message came from. */
enum class Flow {
    /** Money came into the account. */
    IN,
    /** Money left the account (fee is charged on top). */
    OUT,
    /** Fuliza overdraft: credit that's spent immediately; tracked as debt. */
    CREDIT_LINE,
    /** Reversal: the message states the corrected balance directly. */
    ADJUST,
}

enum class TxType(val flow: Flow) {
    RECEIVED(Flow.IN),
    SENT(Flow.OUT),
    PAYBILL(Flow.OUT),
    TILL(Flow.OUT),
    WITHDRAW(Flow.OUT),
    AIRTIME(Flow.OUT),
    DEPOSIT(Flow.IN),
    MSHWARI_SAVE(Flow.OUT),
    MSHWARI_WITHDRAW(Flow.IN),
    FULIZA_LOAN(Flow.CREDIT_LINE),
    FULIZA_REPAY(Flow.OUT),
    REVERSAL(Flow.ADJUST),
    BANK_CREDIT(Flow.IN),
    BANK_DEBIT(Flow.OUT),
}

enum class Confidence { HIGH, MEDIUM, LOW }

data class Transaction(
    /** Provider transaction code (M-Pesa) or bank reference; unique key for duplicate detection. */
    val code: String,
    val provider: Provider,
    val type: TxType,
    val amount: Money,
    val fee: Money,
    /** Display-safe counterparty name, e.g. "JANE WANJIKU" or "KPLC PREPAID". */
    val counterparty: String?,
    val counterpartyPhone: String?,
    /** Paybill account number, if any. */
    val accountNumber: String?,
    val occurredAt: LocalDateTime,
    /** Balance the message reports after this transaction, if it reports one. */
    val balanceAfter: Money?,
    /** For reversals: the code of the transaction being reversed. */
    val reversesCode: String? = null,
    /** For Fuliza messages: total outstanding overdraft. */
    val fulizaOutstanding: Money? = null,
    val confidence: Confidence = Confidence.HIGH,
    /**
     * For bank messages: the M-Pesa code of the other side of the same movement
     * (e.g. M-Pesa → bank deposit). Lets the app mark it as a transfer between the
     * user's own accounts instead of counting it as spending or income.
     */
    val linkedCode: String? = null,
    /** Bank accepted the request but hasn't confirmed completion yet. */
    val pending: Boolean = false,
    /** Card purchases in another currency, e.g. "USD 23.20". [amount] is then unknown until resolved. */
    val foreignAmount: String? = null,
    val parserVersion: Int = PARSER_VERSION,
)

enum class IgnoreReason {
    /** Message failed basic sanity checks (empty, too long). */
    MALFORMED,
    /** From a sender we don't trust and doesn't look like a scam: ordinary chat, OTPs, etc. */
    UNTRUSTED_SENDER,
    /** From a trusted sender but not a transaction: promotions, notices. */
    NOT_A_TRANSACTION,
    /** A failed or declined transaction; no money moved. */
    FAILED_TRANSACTION,
}

sealed interface ParseOutcome {
    data class Parsed(val tx: Transaction) : ParseOutcome
    data class Ignored(val reason: IgnoreReason) : ParseOutcome
    /** Same transaction code was already recorded. */
    data class Duplicate(val code: String) : ParseOutcome
    /** Looks like a scam. The app shows a warning; nothing is recorded as money. */
    /**
     * [imitates] is set when the sender name looks like a trusted one ("MPESA-KE", "C0-OPBANK").
     * The app then also offers "This really is my bank", which adds the sender to the trusted list.
     */
    data class ScamAlert(
        val sender: String,
        val score: Int,
        val signals: List<ScamSignal>,
        val imitates: Provider? = null,
    ) : ParseOutcome
    /** From a trusted sender and looks like money moved, but no rule could read it confidently. */
    data class NeedsReview(val provider: Provider, val why: String, val partial: Transaction? = null) : ParseOutcome
}

enum class ScamSignal(val weight: Int, val explanation: String) {
    LOOKALIKE_SENDER(60, "The sender name imitates M-Pesa or a bank"),
    UPFRONT_FEE(40, "Asks for a fee before giving you a loan or prize"),
    LOAN_BAIT(20, "Unrequested loan offer"),
    IMITATES_PROVIDER(60, "Looks like an M-Pesa or bank confirmation but came from an ordinary number"),
    WRONG_TRANSFER_STORY(35, "Claims money was sent by mistake and asks for it back"),
    ASKS_FOR_PIN(45, "Asks for your PIN or a secret code"),
    PRIZE_OR_URGENCY(25, "Promises a prize or pushes you to act fast"),
    CONTAINS_LINK(15, "Contains a link"),
    ASKS_TO_CALL(10, "Asks you to call or text a number"),
}
