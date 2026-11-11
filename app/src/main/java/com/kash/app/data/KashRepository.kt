package com.kash.app.data

import com.kash.insights.Categorizer
import com.kash.insights.Category
import com.kash.insights.EventSource
import com.kash.insights.HoldingSummary
import com.kash.insights.InMemoryLearnedRules
import com.kash.insights.InvestmentEvent
import com.kash.insights.InvestmentTracker
import com.kash.insights.LearnedRules
import com.kash.insights.MerchantKey
import com.kash.insights.Placement
import com.kash.insights.SchemeRegistry
import com.kash.parser.BalanceChain
import com.kash.parser.Flow
import com.kash.parser.Money
import com.kash.parser.ParseOutcome
import com.kash.parser.Provider
import com.kash.parser.RawSms
import com.kash.parser.ScamSignal
import com.kash.parser.SenderPolicy
import com.kash.parser.SmsPipeline
import com.kash.parser.Transaction
import com.kash.parser.TxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

data class TxItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val amount: Money,
    /** Transaction cost stated in the message (M-Pesa/Airtel/bank charge). Counted as spending. */
    val fee: Money = Money.ZERO,
    val incoming: Boolean,
    val category: Category?,
    val at: LocalDateTime,
    val provider: Provider,
    val pending: Boolean = false,
)

data class Question(
    val txId: String,
    val key: MerchantKey?,
    val prompt: String,
    val choices: List<Category>,
    /** Who it went to / came from, how much, and when — for the "What was it for?" notification. */
    val title: String = "",
    val amount: Money = Money.ZERO,
    val incoming: Boolean = false,
    val at: LocalDateTime? = null,
)

data class AccountCard(
    val id: String,
    val name: String,
    val provider: Provider?,
    /** Balance as the last message stated it (never below zero for M-Pesa). */
    val balance: Money?,
    val updatedAt: LocalDateTime?,
    /** Fuliza still owed on this wallet. What you really have = balance − owed, which can be negative. */
    val owed: Money = Money.ZERO,
    /** True when worked out from a past balance plus later messages, rather than stated by the latest one. */
    val estimated: Boolean = false,
) {
    val realBalance: Money? get() = balance?.let { it - owed }
}

data class ScamWarning(val sender: String, val reasons: List<ScamSignal>, val at: LocalDateTime)

/** "Messages from CO-OP look like Co-op Bank. Is this your bank?" */
data class SenderQuestion(val sender: String, val provider: Provider, val example: Money)

data class MissingMoney(val account: String, val amount: Money, val between: LocalDateTime, val and: LocalDateTime)

data class RepoState(
    /** False only until the very first local read completes. */
    val ready: Boolean = false,
    /** Whether Kash may read SMS. When false, every screen shows how to allow it instead of numbers. */
    val smsAccess: Boolean = false,
    val accounts: List<AccountCard> = emptyList(),
    val transactions: List<TxItem> = emptyList(),
    val questions: List<Question> = emptyList(),
    val holdings: List<HoldingSummary> = emptyList(),
    val scams: List<ScamWarning> = emptyList(),
    val gaps: List<MissingMoney> = emptyList(),
    val senderQuestions: List<SenderQuestion> = emptyList(),
    /** Every wallet/bank Kash found messages from, and which ones the user removed. */
    val seenProviders: List<Provider> = emptyList(),
    val ignoredProviders: Set<Provider> = emptySet(),
    val fulizaOutstanding: Money = Money.ZERO,
    val lastSync: LocalDateTime? = null,
)

/**
 * Single source of truth for the UI. Everything is local, so reads are instant; heavier work
 * (parsing, categorising, balance checks) runs on [Dispatchers.Default], never the main thread.
 */
interface KashRepository {
    val state: StateFlow<RepoState>
    /** Re-reads messages and recomputes. Safe to call any time; called by the background worker. */
    suspend fun sync()
    /** Saves the user's answer to "What was this?" and applies it to every payment to the same place. */
    suspend fun answer(txId: String, category: Category)
    /** The user confirmed (or rejected) an unfamiliar sender as their bank. Re-reads messages. */
    suspend fun answerSender(sender: String, provider: Provider, isMyBank: Boolean)
    /** For accounts whose messages never state a balance (e.g. Co-op deposits). Re-computes. */
    suspend fun setBalance(provider: Provider, balance: Money)
    /** Remove an account from Kash (its messages are ignored) or bring it back. */
    suspend fun setTracked(provider: Provider, tracked: Boolean)
}

/**
 * Runs the parser + categoriser over the user's own SMS. Nothing is invented: with no permission
 * or no money messages, the state is empty and the UI says so.
 */
class LocalRepository(
    /** Reads the phone's messages (empty without permission). */
    private val messages: suspend () -> List<RawSms>,
    private val hasAccess: () -> Boolean,
    private val learned: LearnedRules = InMemoryLearnedRules(),
    private val prefs: KashPrefs? = null,
) : KashRepository {

    private val _state = MutableStateFlow(RepoState())
    override val state: StateFlow<RepoState> = _state.asStateFlow()

    private var parsed: List<Transaction> = emptyList()
    private val lock = Mutex() // a new SMS and an app resume can both trigger sync at once

    override suspend fun sync(): Unit = lock.withLock { withContext(Dispatchers.Default) { syncLocked() } }

    private var senderQuestions: List<SenderQuestion> = emptyList()

    private suspend fun syncLocked() {
        val pipeline = SmsPipeline(senders = SenderPolicy.withConfirmed(prefs?.confirmedSenders().orEmpty()))
        val rejected = prefs?.rejectedSenders().orEmpty()
        val txs = mutableListOf<Transaction>()
        val scams = mutableListOf<ScamWarning>()
        val questions = linkedMapOf<String, SenderQuestion>()
        for (sms in messages()) {
            when (val o = pipeline.process(sms)) {
                is ParseOutcome.Parsed -> txs += o.tx
                is ParseOutcome.NeedsReview -> o.partial?.takeIf { it.amount.isPositive }?.let { txs += it }
                is ParseOutcome.ConfirmSender -> if (o.sender !in rejected) {
                    questions.getOrPut(o.sender) { SenderQuestion(o.sender, o.provider, o.tx.amount) }
                }
                is ParseOutcome.ScamAlert -> scams += ScamWarning(o.sender, o.signals, LocalDateTime.ofInstant(sms.receivedAt, java.time.ZoneId.of("Africa/Nairobi")))
                else -> Unit
            }
        }
        parsed = txs.sortedByDescending { it.occurredAt }
        senderQuestions = questions.values.toList()
        // Only recent scam attempts are worth a warning; months-old ones are just noise.
        val recent = LocalDateTime.now().minusDays(SCAM_WARNING_DAYS)
        _state.value = build(scams.filter { it.at >= recent }.sortedByDescending { it.at })
    }

    override suspend fun answerSender(sender: String, provider: Provider, isMyBank: Boolean) {
        if (isMyBank) prefs?.confirmSender(sender, provider) else prefs?.rejectSender(sender)
        sync()
    }

    override suspend fun setTracked(provider: Provider, tracked: Boolean) {
        prefs?.setTracked(provider, tracked)
        withContext(Dispatchers.Default) { _state.update { build(it.scams) } }
    }

    override suspend fun setBalance(provider: Provider, balance: Money) {
        prefs?.setManualBalance(provider, balance)
        withContext(Dispatchers.Default) { _state.update { build(it.scams) } }
    }

    override suspend fun answer(txId: String, category: Category) {
        // Answered from a notification while Kash was closed: messages may not be read yet.
        if (parsed.none { it.code == txId }) sync()
        answerLoaded(txId, category)
    }

    private suspend fun answerLoaded(txId: String, category: Category) = withContext(Dispatchers.Default) {
        val tx = parsed.firstOrNull { it.code == txId } ?: return@withContext
        val key = MerchantKey.of(tx) ?: MerchantKey.single(tx)
        learned.put(key, category)
        _state.update { build(it.scams) }
    }

    private fun build(scams: List<ScamWarning>): RepoState {
        val ignored = prefs?.ignoredProviders().orEmpty()
        val all = parsed
        // Linked codes come from ALL messages, so an M-Pesa → removed-bank deposit still reads as a transfer.
        val linked = all.mapNotNull { it.linkedCode }.toSet()
        val parsed = all.filter { it.provider !in ignored }
        // Fund account numbers confirmed by the fund itself (e.g. Co-op MMF "CL42326").
        val fundAccounts = parsed.filter { it.type == TxType.FUND_DEPOSIT || it.type == TxType.FUND_REDEMPTION }
            .mapNotNull { t -> t.accountNumber?.let { it.uppercase() to fundSchemeFor(t.provider) } }.toMap()
        val categorizer = Categorizer(learned = learned, linkedCodes = { linked }, fundAccounts = { fundAccounts })
        val items = mutableListOf<TxItem>()
        val questions = mutableListOf<Question>()
        val investEvents = mutableMapOf<String, MutableList<InvestmentEvent>>()

        for (tx in parsed) {
            if (tx.type == TxType.FUND_DEPOSIT || tx.type == TxType.FUND_REDEMPTION) {
                // Confirmation of money already moved: updates savings only (the tracker merges it
                // with the M-Pesa/bank payment if both exist, so it's never counted twice).
                val ev = if (tx.type == TxType.FUND_DEPOSIT) InvestmentEvent.Contribution(tx.amount, tx.occurredAt.toLocalDate(), EventSource.FUND_MESSAGE)
                else InvestmentEvent.Withdrawal(tx.amount, tx.occurredAt.toLocalDate(), EventSource.FUND_MESSAGE)
                investEvents.getOrPut(fundSchemeFor(tx.provider)) { mutableListOf() } += ev
                continue
            }
            if (tx.type == TxType.FULIZA_LOAN) {
                // The borrowed amount isn't spending (it's spent by the payment it covered); its fee is.
                if (tx.fee.isPositive) items += TxItem(
                    id = tx.code + "-fee", title = "Fuliza access fee", subtitle = "M-Pesa",
                    amount = tx.fee, incoming = false, category = Category.FEES, at = tx.occurredAt, provider = tx.provider,
                )
                continue
            }
            var category: Category? = null
            when (val p = categorizer.place(tx)) {
                is Placement.Known -> {
                    category = p.result.category
                    p.result.schemeId?.let { id ->
                        val ev = if (tx.type.flow == Flow.IN) InvestmentEvent.Withdrawal(tx.amount, tx.occurredAt.toLocalDate(), EventSource.MPESA)
                        else InvestmentEvent.Contribution(tx.amount, tx.occurredAt.toLocalDate(), EventSource.MPESA)
                        investEvents.getOrPut(id) { mutableListOf() } += ev
                    }
                }
                is Placement.Ask -> questions += Question(
                    tx.code, p.key, p.prompt, p.choices,
                    title = tx.counterparty ?: label(tx.type), amount = tx.amount,
                    incoming = tx.type.flow == Flow.IN, at = tx.occurredAt,
                )
            }
            items += TxItem(
                id = tx.code,
                title = tx.counterparty ?: label(tx.type),
                subtitle = listOfNotNull(tx.provider.displayName, if (tx.pending) "Processing" else null).joinToString(" · "),
                amount = tx.amount,
                fee = tx.fee,
                incoming = tx.type.flow == Flow.IN,
                category = category,
                at = tx.occurredAt,
                provider = tx.provider,
                pending = tx.pending,
            )
        }

        val gaps = parsed.groupBy { it.provider }.flatMap { (provider, list) ->
            BalanceChain.check(list).map { MissingMoney(provider.displayName, it.difference, it.previous.occurredAt, it.tx.occurredAt) }
        }

        // Fuliza owed: each loan message states the new total owed; each repayment reduces it.
        var fuliza = Money.ZERO
        for (t in parsed.sortedWith(compareBy<Transaction> { it.occurredAt }.thenBy { it.receivedAt })) {
            when (t.type) {
                TxType.FULIZA_LOAN -> fuliza = t.fulizaOutstanding ?: (fuliza + t.amount + t.fee)
                TxType.FULIZA_REPAY -> fuliza = (fuliza - t.amount).let { if (it.cents < 0) Money.ZERO else it }
                else -> Unit
            }
        }

        val accounts = accountsFrom(parsed, fuliza)
        val today = LocalDate.now()
        val holdings = investEvents.mapNotNull { (id, events) ->
            SchemeRegistry.byId(id)?.let { InvestmentTracker.summarize(it, events, today) }
        }

        return RepoState(
            ready = true,
            smsAccess = hasAccess(),
            accounts = accounts,
            transactions = items,
            questions = questions,
            holdings = holdings,
            scams = scams,
            gaps = gaps,
            senderQuestions = senderQuestions.filter { it.provider !in ignored },
            seenProviders = all.map { it.provider }.distinct().sortedBy { if (it.isMobileMoney) 0 else 1 },
            ignoredProviders = ignored,
            fulizaOutstanding = fuliza,
            lastSync = LocalDateTime.now(),
        )
    }

    /**
     * One card per wallet/bank. Balance = the latest balance a message stated, or one the user typed in
     * (whichever is newer), moved up/down by every later message that doesn't state a balance.
     * Banks whose messages never state a balance (Co-op deposits) show "Set balance" until the user does.
     */
    private fun accountsFrom(all: List<Transaction>, fuliza: Money): List<AccountCard> {
        val manual = prefs?.manualBalances().orEmpty()
        return all.filter { it.type.flow != Flow.NONE }.groupBy { it.provider }.map { (provider, list) ->
            val ordered = list.sortedWith(compareBy<Transaction> { it.occurredAt }.thenBy { it.receivedAt })
            val stated = ordered.lastOrNull { it.balanceAfter != null }
            val typed = manual[provider]
            var balance: Money? = null
            var at: LocalDateTime? = null
            var after: LocalDateTime? = null
            if (typed != null && (stated == null || typed.second.isAfter(stated.occurredAt))) {
                balance = typed.first; at = typed.second; after = typed.second
            } else if (stated != null) {
                balance = stated.balanceAfter; at = stated.occurredAt; after = stated.occurredAt
            }
            var estimated = false
            if (balance != null && after != null) {
                ordered.filter { it.occurredAt.isAfter(after) && it.balanceAfter == null }.forEach { t ->
                    balance = balance!! + BalanceChain.delta(t); at = t.occurredAt; estimated = true
                }
            }
            AccountCard(
                provider.name, provider.displayName, provider, balance, at,
                owed = if (provider == Provider.MPESA) fuliza else Money.ZERO,
                estimated = estimated,
            )
        }.sortedBy { if (it.provider?.isMobileMoney == true) 0 else 1 }
    }

    private fun fundSchemeFor(provider: Provider) = when (provider) {
        Provider.COOP -> "coopmmf"
        else -> "fund-${provider.name.lowercase()}"
    }

    private companion object {
        const val SCAM_WARNING_DAYS = 7L
    }

    private fun label(type: TxType) = when (type) {
        TxType.AIRTIME -> "Airtime"
        TxType.WITHDRAW -> "Cash withdrawal"
        TxType.DEPOSIT -> "Cash deposit"
        TxType.MSHWARI_SAVE -> "To M-Shwari"
        TxType.MSHWARI_WITHDRAW -> "From M-Shwari"
        TxType.FULIZA_REPAY -> "Fuliza repayment"
        TxType.REVERSAL -> "Reversal"
        else -> "Transaction"
    }
}
