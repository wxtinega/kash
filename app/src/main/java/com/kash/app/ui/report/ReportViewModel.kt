package com.kash.app.ui.report

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kash.app.core.HumanErrors
import com.kash.app.data.KashRepository
import com.kash.app.data.RepoState
import com.kash.insights.MonthlyReport
import com.kash.insights.MonthlyReports
import com.kash.insights.ReportEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

data class ReportUi(
    val ready: Boolean = false,
    val smsAccess: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val report: MonthlyReport? = null,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val exporting: Boolean = false,
)

sealed interface ReportEvent {
    data class PdfSaved(val uri: Uri, val fileName: String) : ReportEvent
    data class Message(val text: String) : ReportEvent
}

fun RepoState.reportEntries(): List<ReportEntry> =
    transactions.map { ReportEntry(it.at.toLocalDate(), it.amount, it.fee, it.incoming, it.category, it.title) }

class ReportViewModel(private val repo: KashRepository) : ViewModel() {

    /** Early in a month, last month's (complete) report is the useful one. */
    private val month = MutableStateFlow(
        LocalDate.now().let { if (it.dayOfMonth <= 3) YearMonth.from(it).minusMonths(1) else YearMonth.from(it) },
    )
    private val exporting = MutableStateFlow(false)
    // A channel keeps an event until the screen collects it (e.g. a PDF started from the notification).
    private val _events = Channel<ReportEvent>(Channel.BUFFERED)
    val events: Flow<ReportEvent> = _events.receiveAsFlow()

    val ui: StateFlow<ReportUi> = combine(repo.state, month, exporting) { s, m, busy ->
        val entries = s.reportEntries()
        val earliest = entries.minOfOrNull { YearMonth.from(it.date) } ?: m
        ReportUi(
            ready = s.ready,
            smsAccess = s.smsAccess,
            month = m,
            report = if (s.ready) MonthlyReports.build(entries, m) else null,
            canGoBack = m > earliest,
            canGoForward = m < YearMonth.now(),
            exporting = busy,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUi())

    fun previous() { month.value = month.value.minusMonths(1) }
    fun next() { if (month.value < YearMonth.now()) month.value = month.value.plusMonths(1) }
    fun show(m: YearMonth) { month.value = m }

    /** Builds the PDF off the main thread and saves it to Downloads. The button never freezes. */
    fun exportPdf(context: Context, m: YearMonth = month.value) {
        if (exporting.value) return
        exporting.value = true
        val app = context.applicationContext
        viewModelScope.launch {
            try {
                val report = withContext(Dispatchers.Default) { MonthlyReports.build(repo.state.value.reportEntries(), m) }
                val saved = withContext(Dispatchers.IO) { ReportPdf.saveToDownloads(app, report) }
                _events.trySend(ReportEvent.PdfSaved(saved.first, saved.second))
            } catch (t: Throwable) {
                _events.trySend(ReportEvent.Message(HumanErrors.message(t)))
            } finally {
                exporting.value = false
            }
        }
    }
}
