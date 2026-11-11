package com.kash.app.ui.report

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kash.app.ui.components.ButtonStyle
import com.kash.app.ui.components.EmptyState
import com.kash.app.ui.components.GlassCard
import com.kash.app.ui.components.KashButton
import com.kash.app.ui.components.SkeletonRow
import com.kash.app.ui.components.pressable
import com.kash.app.ui.components.rememberShowLoading
import com.kash.app.ui.display
import com.kash.app.ui.icon
import com.kash.app.ui.icons.Lucide
import com.kash.app.ui.theme.Kash
import com.kash.app.ui.theme.Space
import com.kash.insights.CategoryTotal
import com.kash.insights.MonthlyReport
import com.kash.parser.Money
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

@Composable
fun ReportScreen(vm: ReportViewModel, hidden: Boolean, snackbar: SnackbarHostState, contentPadding: PaddingValues) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = Kash.colors
    val context = LocalContext.current
    val showSkeleton = rememberShowLoading(!ui.ready)

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is ReportEvent.Message -> snackbar.showSnackbar(e.text)
                is ReportEvent.PdfSaved -> {
                    val r = snackbar.showSnackbar("Saved to Downloads as ${e.fileName}", actionLabel = "Open")
                    if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).setDataAndType(e.uri, "application/pdf")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } catch (_: ActivityNotFoundException) {
                            snackbar.showSnackbar("No PDF viewer found. The file is in your Downloads folder.")
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen,
            top = contentPadding.calculateTopPadding() + Space.s,
            bottom = contentPadding.calculateBottomPadding() + Space.l,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item(key = "title") {
            Text("Monthly report", style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading() })
        }
        item(key = "month") { MonthSwitcher(ui, vm::previous, vm::next) }

        val report = ui.report
        when {
            !ui.ready -> if (showSkeleton) items(4) { SkeletonRow() }
            !ui.smsAccess -> item(key = "no-access") {
                EmptyState(Lucide.Lock, "No messages to report on yet", "Allow SMS access from Home, and Kash will build this report from your M-Pesa and bank messages.")
            }
            report == null || report.isEmpty -> item(key = "empty") {
                EmptyState(Lucide.ChartNoAxesColumn, "Nothing in ${ui.month.format(MONTH_FMT)}", "No money messages from this month. Try another month with the arrows above.")
            }
            else -> {
                item(key = "totals") { Totals(report, hidden) }
                item(key = "actions") {
                    KashButton(
                        "Download as PDF",
                        { vm.exportPdf(context) },
                        Modifier.fillMaxWidth(),
                        style = ButtonStyle.SECONDARY,
                        icon = Lucide.Receipt,
                        busy = ui.exporting,
                    )
                }
                if (report.highlights.isNotEmpty()) item(key = "highlights") { Highlights(report) }
                item(key = "categories") { CategoryChart(report, hidden) }
                item(key = "daily") { DailyChart(report, hidden) }
                if (report.topPayees.isNotEmpty()) item(key = "payees") { TopPayees(report, hidden) }
            }
        }
    }
}

@Composable
private fun MonthSwitcher(ui: ReportUi, onBack: () -> Unit, onForward: () -> Unit) {
    val c = Kash.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        ArrowButton(Lucide.ChevronRight, "Previous month", ui.canGoBack, onBack, flip = true)
        Text(
            ui.month.format(MONTH_FMT),
            style = MaterialTheme.typography.titleMedium,
            color = c.text,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        ArrowButton(Lucide.ChevronRight, "Next month", ui.canGoForward, onForward)
    }
}

@Composable
private fun ArrowButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, flip: Boolean = false) {
    val c = Kash.colors
    Box(
        Modifier.size(Space.touch).pressable(onClick, CircleShape, enabled = enabled, onClickLabel = label),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, label,
            Modifier.size(22.dp).graphicsLayer { scaleX = if (flip) -1f else 1f },
            tint = if (enabled) c.text else c.textMuted.copy(alpha = 0.35f),
        )
    }
}

@Composable
private fun Totals(r: MonthlyReport, hidden: Boolean) {
    val c = Kash.colors
    GlassCard(strong = true) {
        Text("Spent", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
        Text(r.spent.display(hidden), style = MaterialTheme.typography.displaySmall, color = c.text)
        Spacer(Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            Figure("Money in", r.moneyIn.display(hidden), Modifier.weight(1f))
            Figure("Saved", r.saved.display(hidden), Modifier.weight(1f))
            Figure("Costs", r.fees.display(hidden), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    val c = Kash.colors
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.textMuted, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Highlights(r: MonthlyReport) {
    val c = Kash.colors
    GlassCard {
        Text("What stood out", style = MaterialTheme.typography.titleSmall, color = c.text)
        Spacer(Modifier.height(Space.xs))
        r.highlights.take(4).forEach { h ->
            Row(Modifier.padding(vertical = Space.xxs)) {
                Text("•", color = c.accentText, modifier = Modifier.width(16.dp), style = MaterialTheme.typography.bodyMedium)
                Text(h, style = MaterialTheme.typography.bodyMedium, color = c.text)
            }
        }
    }
}

/**
 * Where the money went: one horizontal bar per category, ranked. Single hue (it's one measure);
 * every bar has its label and value in text, so nothing depends on colour.
 */
@Composable
private fun CategoryChart(r: MonthlyReport, hidden: Boolean) {
    val c = Kash.colors
    val barColor = if (c.isDark) c.accent else c.accentText // 3:1+ against the surface in both themes
    val shown: List<CategoryTotal> = r.byCategory.take(7).let { top ->
        val rest = r.byCategory.drop(7)
        if (rest.isEmpty()) top
        else top + CategoryTotal(null, rest.fold(Money.ZERO) { a, x -> a + x.amount }, rest.sumOf { it.count }, rest.sumOf { it.share })
    }
    val max = shown.maxOfOrNull { it.amount.cents }?.coerceAtLeast(1) ?: 1
    GlassCard {
        Text("Where your money went", style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Space.s))
        shown.forEachIndexed { i, t ->
            val label = when {
                t.category != null -> t.category!!.label
                i == shown.lastIndex && r.byCategory.size > 7 -> "Everything else"
                else -> "Not sorted yet"
            }
            Column(
                Modifier.padding(vertical = 6.dp).semantics(mergeDescendants = true) {
                    contentDescription = "$label, ${if (hidden) "amount hidden" else t.amount.format()}, ${Math.round(t.share * 100)} percent"
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(t.category?.icon() ?: Lucide.CircleHelp, null, Modifier.size(16.dp), tint = c.textMuted)
                    Spacer(Modifier.width(Space.s))
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${t.amount.display(hidden)} · ${Math.round(t.share * 100)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textMuted,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.skeleton)) {
                    Box(
                        Modifier
                            .fillMaxWidth((t.amount.cents.toFloat() / max).coerceIn(0.02f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor),
                    )
                }
            }
        }
    }
}

/** Spending per day. Tap a bar to see that day's total. */
@Composable
private fun DailyChart(r: MonthlyReport, hidden: Boolean) {
    val c = Kash.colors
    val barColor = if (c.isDark) c.accent else c.accentText
    val track = c.skeleton
    var selected by remember(r.month) { mutableStateOf<Int?>(null) }
    val max = r.daily.maxOfOrNull { it.cents }?.coerceAtLeast(1) ?: 1
    val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    GlassCard {
        Text("Spending by day", style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.semantics { heading() })
        val caption = selected?.let { d -> "${r.month.atDay(d + 1).format(dayFmt)}: ${r.daily[d].display(hidden)}" }
            ?: r.biggestDay?.let { "Biggest day: ${it.first.format(dayFmt)}, ${it.second.display(hidden)}. Tap a bar for any day." }
            ?: "Tap a bar to see that day."
        Text(caption, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        Spacer(Modifier.height(Space.s))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .semantics { contentDescription = "Daily spending chart. " + (r.biggestDay?.let { "Highest on day ${it.first.dayOfMonth}." } ?: "") }
                .pointerInput(r.month) {
                    detectTapGestures { pos ->
                        val slot = size.width.toFloat() / r.daily.size
                        selected = (pos.x / slot).toInt().coerceIn(0, r.daily.lastIndex)
                    }
                },
        ) {
            val n = r.daily.size
            val gap = 2.dp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            r.daily.forEachIndexed { i, v ->
                val h = if (v.cents <= 0) 0f else (v.cents.toFloat() / max * size.height).coerceAtLeast(3.dp.toPx())
                val x = i * (w + gap)
                // faint full-height track so empty days are still visible as slots
                drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(2.dp.toPx()))
                if (h > 0f) {
                    drawRoundRect(
                        color = if (selected == null || selected == i) barColor else barColor.copy(alpha = 0.45f),
                        topLeft = Offset(x, size.height - h),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Space.xxs)) {
            Text("1", style = MaterialTheme.typography.labelSmall, color = c.textMuted, modifier = Modifier.weight(1f))
            Text("${r.daily.size}", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
        }
    }
}

@Composable
private fun TopPayees(r: MonthlyReport, hidden: Boolean) {
    val c = Kash.colors
    GlassCard {
        Text("Where you paid most", style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Space.xs))
        r.topPayees.forEachIndexed { i, p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = c.textMuted, modifier = Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.payee, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${p.count} payment${if (p.count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
                Text(p.amount.display(hidden), style = MaterialTheme.typography.bodyMedium, color = c.text)
            }
        }
    }
}
