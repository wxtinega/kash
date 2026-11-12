package com.kash.app.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kash.app.data.KashRepository
import com.kash.app.data.TxItem
import com.kash.app.ui.common.TxRow
import com.kash.app.ui.components.EmptyState
import com.kash.app.ui.components.Pill
import com.kash.app.ui.components.SearchField
import com.kash.app.ui.components.SkeletonRow
import com.kash.app.ui.components.rememberShowLoading
import com.kash.app.ui.dayLabel
import com.kash.app.ui.icons.Lucide
import com.kash.app.ui.theme.Kash
import com.kash.app.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

enum class TxFilter(val label: String) { ALL("All"), OUT("Money out"), IN("Money in"), UNSORTED("Not sorted") }

data class ActivityUi(
    val ready: Boolean = false,
    val query: String = "",
    val activeFilter: TxFilter = TxFilter.ALL,
    val days: List<Pair<LocalDate, List<TxItem>>> = emptyList(),
    val totalCount: Int = 0,
)

class ActivityViewModel(repo: KashRepository) : ViewModel() {
    /**
     * What's in the search box. Plain Compose state, updated on the main thread the instant a key is
     * pressed. The box must never show a value that went through a background flow: when typing is
     * faster than that round trip, the box receives an older copy and the cursor jumps or letters
     * come out in the wrong order.
     */
    var queryText by androidx.compose.runtime.mutableStateOf("")
        private set
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(TxFilter.ALL)

    /** Recomputed on every keystroke. Filtering local data takes microseconds, so no debounce or spinner. */
    val ui: StateFlow<ActivityUi> = combine(repo.state, query, filter) { s, q, f ->
        val needle = q.trim().lowercase()
        val matches = s.transactions.filter { t ->
            val passesFilter = when (f) {
                TxFilter.ALL -> true
                TxFilter.OUT -> !t.incoming
                TxFilter.IN -> t.incoming
                TxFilter.UNSORTED -> t.category == null
            }
            passesFilter && (
                needle.isEmpty() ||
                    t.title.lowercase().contains(needle) ||
                    (t.category?.label?.lowercase()?.contains(needle) == true) ||
                    t.amount.format().replace(",", "").contains(needle.replace(",", ""))
                )
        }
        ActivityUi(
            ready = s.ready,
            query = q,
            activeFilter = f,
            days = matches.groupBy { it.at.toLocalDate() }.toList().sortedByDescending { it.first },
            totalCount = s.transactions.size,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUi())

    fun setQuery(q: String) {
        queryText = q
        query.value = q // the list filters from this, a moment later; the box doesn't wait for it
    }
    fun setFilter(f: TxFilter) { filter.value = f }
}

@Composable
fun ActivityScreen(vm: ActivityViewModel, hidden: Boolean, contentPadding: PaddingValues) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = Kash.colors
    val showSkeleton = rememberShowLoading(!ui.ready)

    LazyColumn(
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen,
            top = contentPadding.calculateTopPadding() + Space.s,
            bottom = contentPadding.calculateBottomPadding() + Space.l,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        item(key = "title") {
            Text("Activity", style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading() })
        }
        item(key = "search") {
            SearchField(vm.queryText, vm::setQuery, "Search name, category or amount", Modifier.fillMaxWidth().padding(top = Space.s))
        }
        item(key = "filters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                items(TxFilter.entries.toList(), key = { it.name }) { f -> Pill(f.label, ui.activeFilter == f, { vm.setFilter(f) }) }
            }
        }

        when {
            !ui.ready -> if (showSkeleton) items(6) { SkeletonRow() }
            ui.totalCount == 0 -> item(key = "empty") {
                EmptyState(Lucide.Inbox, "Nothing here yet", "Transactions appear here from your M-Pesa and bank messages. If you haven't allowed SMS access yet, you can do it from Home.")
            }
            ui.days.isEmpty() -> item(key = "no-results") {
                EmptyState(
                    Lucide.Search,
                    "No matches",
                    if (ui.query.isNotBlank()) "Nothing matches \"${ui.query}\". Try a name, a category like Food, or an amount." else "No transactions in this filter.",
                    actionLabel = "Clear search",
                    onAction = { vm.setQuery(""); vm.setFilter(TxFilter.ALL) },
                )
            }
            else -> ui.days.forEach { (day, txs) ->
                item(key = "d-$day") {
                    Text(
                        day.dayLabel(),
                        style = MaterialTheme.typography.labelLarge,
                        color = c.textMuted,
                        modifier = Modifier.padding(top = Space.m, bottom = Space.xxs).animateItem(),
                    )
                }
                items(txs, key = { it.id }) { TxRow(it, hidden, Modifier.animateItem()) }
            }
        }
    }
}
