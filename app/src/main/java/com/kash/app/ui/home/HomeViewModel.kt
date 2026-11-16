package com.kash.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kash.app.core.HumanErrors
import com.kash.app.data.AccountCard
import com.kash.app.data.KashRepository
import com.kash.app.data.MissingMoney
import com.kash.app.data.Question
import com.kash.app.data.ScamWarning
import com.kash.app.data.SenderQuestion
import com.kash.parser.Provider
import com.kash.app.data.TxItem
import com.kash.insights.Category
import com.kash.insights.BudgetStatus
import com.kash.insights.Budgets
import com.kash.insights.MonthlyReports
import com.kash.app.data.BudgetStore
import com.kash.app.ui.report.reportEntries
import java.time.YearMonth
import com.kash.insights.SafeToSpend
import com.kash.parser.Money
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUi(
    val ready: Boolean = false,
    val smsAccess: Boolean = true,
    /** True once at least one real money message has been found. No data = no made-up figures. */
    val hasData: Boolean = false,
    val netWorth: Money = Money.ZERO,
    val accounts: List<AccountCard> = emptyList(),
    val savingsTotal: Money = Money.ZERO,
    val safe: SafeToSpend.Result? = null,
    val nextIncome: LocalDate? = null,
    /** Unsorted payments from the last [RECENT_DAYS] days: asked about on Home. */
    val questions: List<Question> = emptyList(),
    /** Older unsorted payments: hidden until the user taps "Sort older payments". */
    val olderQuestions: List<Question> = emptyList(),
    val showOlder: Boolean = false,
    val quote: DailyQuote = DailyQuotes.forDay(),
    val recent: List<TxItem> = emptyList(),
    val scams: List<ScamWarning> = emptyList(),
    val gaps: List<MissingMoney> = emptyList(),
    val fuliza: Money = Money.ZERO,
    val senderQuestions: List<SenderQuestion> = emptyList(),
    /** Budgets (optional). Empty and hidden unless switched on in Settings. */
    val budgetsOn: Boolean = false,
    val budgets: List<BudgetStatus> = emptyList(),
    /** Suggested limits from last month's spending; key null = all spending. */
    val budgetSuggestions: Map<Category?, Money> = emptyMap(),
)

class HomeViewModel(private val repo: KashRepository, private val budgetStore: BudgetStore) : ViewModel() {

    /** Answers shown immediately, before they're saved (optimistic UI). */
    private val optimistic = MutableStateFlow<Map<String, Category>>(emptyMap())
    private val showOlder = MutableStateFlow(false)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    val ui: StateFlow<HomeUi> = combine(repo.state, optimistic, showOlder, budgetStore.state) { s, opt, older, budgetSettings ->
        val today = LocalDate.now()
        val cutoff = today.minusDays(RECENT_DAYS).atStartOfDay()
        val open = s.questions.filter { it.txId !in opt }
        val (recentQs, olderQs) = open.partition { it.at == null || !it.at.isBefore(cutoff) }
        val nextIncome = today.plusMonths(1).withDayOfMonth(1)
        val balances = s.accounts.fold(Money.ZERO) { acc, a -> acc + (a.balance ?: Money.ZERO) }
        val savings = s.holdings.fold(Money.ZERO) { acc, h -> acc + h.value }
        val spentToday = s.transactions
            .filter { !it.incoming && it.at.toLocalDate() == today && it.category?.isSpending != false }
            .fold(Money.ZERO) { acc, t -> acc + t.amount + t.fee }
        // Budgets: only worked out when switched on.
        val thisMonth = YearMonth.from(today)
        val entries = if (budgetSettings.enabled) s.reportEntries() else emptyList()
        val budgetStatus = if (budgetSettings.enabled && budgetSettings.budgets.isNotEmpty())
            Budgets.status(budgetSettings.budgets, MonthlyReports.build(entries, thisMonth), today) else emptyList()
        val lastMonth = if (budgetSettings.enabled) MonthlyReports.build(entries, thisMonth.minusMonths(1)).takeIf { !it.isEmpty } else null
        val recent = s.transactions.take(6).map { t -> opt[t.id]?.let { t.copy(category = it) } ?: t }
        HomeUi(
            ready = s.ready,
            smsAccess = s.smsAccess,
            hasData = s.accounts.isNotEmpty() || s.transactions.isNotEmpty(),
            netWorth = balances + savings - s.fulizaOutstanding,
            accounts = s.accounts,
            savingsTotal = savings,
            // Only meaningful with real balances; otherwise it would show a made-up "money is short".
            safe = if (s.ready && s.accounts.isNotEmpty()) SafeToSpend.compute(
                SafeToSpend.Input(balances, Money.ZERO, Money.ZERO, s.fulizaOutstanding, today, nextIncome, spentToday),
            ) else null,
            nextIncome = nextIncome,
            questions = recentQs,
            olderQuestions = olderQs,
            showOlder = older && olderQs.isNotEmpty(),
            quote = DailyQuotes.forDay(today),
            recent = recent,
            scams = s.scams,
            gaps = s.gaps,
            fuliza = s.fulizaOutstanding,
            senderQuestions = s.senderQuestions,
            budgetsOn = budgetSettings.enabled,
            budgets = budgetStatus,
            budgetSuggestions = if (lastMonth == null) emptyMap() else
                (listOf<Category?>(null) + Budgets.budgetable).mapNotNull { c -> Budgets.suggest(lastMonth, c)?.let { c to it } }.toMap(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    fun setBudget(category: Category?, limit: Money) = budgetStore.set(category, limit)
    fun removeBudget(category: Category?) = budgetStore.remove(category)

    fun setShowOlder(show: Boolean) = showOlder.update { show }

    fun answerSender(q: SenderQuestion, isMyBank: Boolean) {
        viewModelScope.launch {
            try { repo.answerSender(q.sender, q.provider, isMyBank) } catch (t: Throwable) { _messages.tryEmit(HumanErrors.message(t)) }
        }
    }

    fun stopTracking(provider: Provider) {
        viewModelScope.launch {
            try { repo.setTracked(provider, false) } catch (t: Throwable) { _messages.tryEmit(HumanErrors.message(t)) }
        }
    }

    fun setBalance(provider: Provider, balance: Money) {
        viewModelScope.launch {
            try { repo.setBalance(provider, balance) } catch (t: Throwable) { _messages.tryEmit(HumanErrors.message(t)) }
        }
    }

    /** The card disappears instantly; if saving fails, it comes back with a plain-language note. */
    fun answer(q: Question, category: Category) {
        optimistic.update { it + (q.txId to category) }
        viewModelScope.launch {
            try {
                repo.answer(q.txId, category)
            } catch (t: Throwable) {
                _messages.tryEmit(HumanErrors.message(t))
            } finally {
                optimistic.update { it - q.txId }
            }
        }
    }

    companion object {
        const val RECENT_DAYS = 7L
    }
}
