package com.kash.app.ui.savings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kash.app.data.KashRepository
import com.kash.app.ui.components.EmptyState
import com.kash.app.ui.components.GlassCard
import com.kash.app.ui.components.IconDot
import com.kash.app.ui.components.SkeletonRow
import com.kash.app.ui.components.rememberShowLoading
import com.kash.app.ui.display
import com.kash.app.ui.icons.Lucide
import com.kash.app.ui.theme.Kash
import com.kash.app.ui.theme.Space
import com.kash.insights.HoldingSummary
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SavingsScreen(repo: KashRepository, hidden: Boolean, contentPadding: PaddingValues) {
    val state by repo.state.collectAsStateWithLifecycle()
    val c = Kash.colors
    val showSkeleton = rememberShowLoading(!state.ready)

    LazyColumn(
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen,
            top = contentPadding.calculateTopPadding() + Space.s,
            bottom = contentPadding.calculateBottomPadding() + Space.l,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item(key = "title") {
            Text("Savings & investments", style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading() })
        }
        when {
            !state.ready -> if (showSkeleton) items(3) { SkeletonRow() }
            state.holdings.isEmpty() -> item(key = "empty") {
                EmptyState(
                    Lucide.PiggyBank,
                    "No savings found yet",
                    "When you pay into a money market fund, SACCO, M-Shwari or T-bills, Kash tracks it here automatically.",
                )
            }
            else -> items(state.holdings, key = { it.scheme.id }) { HoldingCard(it, hidden, Modifier.animateItem()) }
        }
    }
}

@Composable
private fun HoldingCard(h: HoldingSummary, hidden: Boolean, modifier: Modifier = Modifier) {
    val c = Kash.colors
    val next = h.nextCheckIn?.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
    GlassCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconDot(Lucide.TrendingUp)
            Spacer(Modifier.width(Space.m))
            Text(h.scheme.name, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(Space.s))
        Text(h.value.display(hidden), style = MaterialTheme.typography.headlineSmall, color = c.text)
        Text(
            when {
                h.lastReport == null -> "Based on what you've put in. Kash will ask for your balance${next?.let { " on $it" } ?: ""}."
                h.isEstimate -> "Estimate from your last check-in"
                else -> "Your latest balance"
            },
            style = MaterialTheme.typography.bodySmall,
            color = c.textMuted,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            "Put in ${h.contributed.display(hidden)} · Taken out ${h.withdrawn.display(hidden)} · ${h.scheme.liquidity}",
            style = MaterialTheme.typography.bodySmall,
            color = c.textMuted,
        )
    }
}
