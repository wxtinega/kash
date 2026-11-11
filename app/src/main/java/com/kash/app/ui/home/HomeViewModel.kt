package com.kash.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kash.app.core.HumanErrors
import com.kash.app.data.AccountCard
import com.kash.app.data.KashRepository
import com.kash.app.data.MissingMoney
import com.kash.app.data.Question
import com.kash.app.data.ScamWarning
import com.kash.app.data.TxItem
import com.kash.insights.Category
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
    val questions: List<Question> = emptyList(),
    val recent: List<TxItem> = emptyList(),
    val scams: List<ScamWarning> = emptyList(),
    val gaps: List<MissingMoney> = emptyList(),
    val fuliza: Money = Money.ZERO,
)

class HomeViewModel(private val repo: KashRepository) : ViewModel() {

    /** Answers shown immediately, before they're saved (optimistic UI). */
    private val optimistic = MutableStateFlow<Map<String, Category>>(emptyMap())
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    val ui: StateFlow<HomeUi> = combine(repo.state, optimistic) { s, opt ->
        val today = LocalDate.now()
        val nextIncome = today.plusMonths(1).withDayOfMonth(1)
        val balances = s.accounts.fold(Money.ZERO) { acc, a -> acc + (a.balance ?: Money.ZERO) }
        val savings = s.holdings.fold(Money.ZERO) { acc, h -> acc + h.value }
        val spentToday = s.transactions
            .filter { !it.incoming && it.at.toLocalDate() == today && it.category?.isSpending != false }
            .fold(Money.ZERO) { acc, t -> acc + t.amount + t.fee }
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
            questions = s.questions.filter { it.txId !in opt },
            recent = recent,
            scams = s.scams,
            gaps = s.gaps,
            fuliza = s.fulizaOutstanding,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

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
}
