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

data class Question(val txId: String, val key: MerchantKey?, val prompt: String, val choices: List<Category>)

data class AccountCard(
    val id: String,
    val name: String,
    val provider: Provider?,
    /** Balance as the last message stated it (never below zero for M-Pesa). */
    val balance: Money?,
    val updatedAt: LocalDateTime?,
    /** Fuliza still owed on this wallet. What you really have = balance − owed, which can be negative. */
    val owed: Money = Money.ZERO,
) {
    val realBalance: Money? get() = balance?.let { it - owed }
}

data class ScamWarning(val sender: String, val reasons: List<ScamSignal>, val at: LocalDateTime)

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
) : KashRepository {

    private val _state = MutableStateFlow(RepoState())
    override val state: StateFlow<RepoState> = _state.asStateFlow()

    private var parsed: List<Transaction> = emptyList()
    private val lock = Mutex() // a new SMS and an app resume can both trigger sync at once

    override suspend fun sync(): Unit = lock.withLock { withContext(Dispatchers.Default) { syncLocked() } }

    private suspend fun syncLocked() {
        val pipeline = SmsPipeline()
        val txs = mutableListOf<Transaction>()
        val scams = mutableListOf<ScamWarning>()
        for (sms in messages()) {
            when (val o = pipeline.process(sms)) {
                is ParseOutcome.Parsed -> txs += o.tx
                is ParseOutcome.NeedsReview -> o.partial?.takeIf { it.amount.isPositive }?.let { txs += it }
                is ParseOutcome.ScamAlert -> scams += ScamWarning(o.sender, o.signals, LocalDateTime.ofInstant(sms.receivedAt, java.time.ZoneId.of("Africa/Nairobi")))
                else -> Unit
            }
        }
        parsed = txs.sortedByDescending { it.occurredAt }
        // Only recent scam attempts are worth a warning; months-old ones are just noise.
        val recent = LocalDateTime.now().minusDays(SCAM_WARNING_DAYS)
        _state.value = build(scams.filter { it.at >= recent }.sortedByDescending { it.at })
    }

    override suspend fun answer(txId: String, category: Category) = withContext(Dispatchers.Default) {
        val tx = parsed.firstOrNull { it.code == txId } ?: return@withContext
        val key = MerchantKey.of(tx) ?: MerchantKey.single(tx)
        learned.put(key, category)
        _state.update { build(it.scams) }
    }

    private fun build(scams: List<ScamWarning>): RepoState {
        val linked = parsed.mapNotNull { it.linkedCode }.toSet()
        val categorizer = Categorizer(learned = learned, linkedCodes = { linked })
        val items = mutableListOf<TxItem>()
        val questions = mutableListOf<Question>()
        val investEvents = mutableMapOf<String, MutableList<InvestmentEvent>>()

        for (tx in parsed) {
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
                is Placement.Ask -> questions += Question(tx.code, p.key, p.prompt, p.choices)
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

        val accounts = parsed.filter { it.balanceAfter != null }
            .groupBy { it.provider }
            .map { (provider, list) ->
                val latest = list.maxBy { it.occurredAt }
                AccountCard(
                    provider.name, provider.displayName, provider, latest.balanceAfter, latest.occurredAt,
                    owed = if (provider == Provider.MPESA) fuliza else Money.ZERO,
                )
            }
            .sortedBy { if (it.provider == Provider.MPESA) 0 else 1 }

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
            fulizaOutstanding = fuliza,
            lastSync = LocalDateTime.now(),
        )
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
