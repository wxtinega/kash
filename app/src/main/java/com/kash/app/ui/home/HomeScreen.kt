package com.kash.app.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kash.app.data.AccountCard
import androidx.compose.runtime.setValue
import com.kash.app.data.Question
import com.kash.app.ui.common.TxRow
import com.kash.app.ui.components.ButtonStyle
import com.kash.app.ui.components.EmptyState
import com.kash.app.ui.components.GlassCard
import com.kash.app.ui.components.KashButton
import com.kash.app.ui.components.Notice
import com.kash.app.ui.components.Pill
import com.kash.app.ui.components.Skeleton
import com.kash.app.ui.components.SkeletonRow
import com.kash.app.ui.components.Tone
import com.kash.app.ui.components.pressable
import com.kash.app.ui.components.rememberShowLoading
import com.kash.app.ui.display
import com.kash.app.ui.icon
import com.kash.app.ui.icons.Lucide
import com.kash.app.ui.spoken
import com.kash.app.ui.theme.Kash
import com.kash.app.ui.theme.Space
import com.kash.insights.Category
import com.kash.insights.OUTGOING_CATEGORIES
import com.kash.insights.SafeToSpend
import com.kash.parser.Provider
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    hidden: Boolean,
    onToggleHidden: () -> Unit,
    onSeeAll: () -> Unit,
    onRequestSms: () -> Unit,
    snackbar: SnackbarHostState,
    contentPadding: PaddingValues,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    var editing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<AccountCard?>(null) }
    editing?.let { acct ->
        SetBalanceDialog(
            acct,
            onDismiss = { editing = null },
            onSave = { m -> acct.provider?.let { vm.setBalance(it, m) }; editing = null },
            onStopTracking = { acct.provider?.let { vm.stopTracking(it) }; editing = null },
        )
    }
    val showSkeleton = rememberShowLoading(!ui.ready)

    LazyColumn(
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen,
            top = contentPadding.calculateTopPadding() + Space.s,
            bottom = contentPadding.calculateBottomPadding() + Space.l,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item(key = "header") { Header(ui, hidden, onToggleHidden, showSkeleton) }

        if (!ui.ready) {
            if (showSkeleton) items(4) { SkeletonRow() }
            return@LazyColumn
        }

        if (!ui.smsAccess) {
            item(key = "no-access") {
                EmptyState(
                    Lucide.Lock,
                    "Kash can't see your messages yet",
                    "Allow access to your SMS and Kash will build your balances and spending from your M-Pesa and bank messages. Everything stays on this phone.",
                    actionLabel = "Allow access",
                    onAction = onRequestSms,
                )
            }
            return@LazyColumn
        }

        if (!ui.hasData) {
            item(key = "no-data") {
                EmptyState(
                    Lucide.Inbox,
                    "No money messages found yet",
                    "Kash looked through the last 6 months of messages and found no M-Pesa or bank transactions. New ones will appear here as they arrive.",
                )
            }
            return@LazyColumn
        }

        ui.safe?.let { safe -> item(key = "safe") { SafeToSpendCard(safe, ui, hidden) } }

        if (ui.accounts.isNotEmpty() || ui.savingsTotal.isPositive) {
            item(key = "accounts") { Accounts(ui.accounts, ui.savingsTotal, hidden, onSetBalance = { editing = it }) }
        }

        ui.senderQuestions.forEach { q ->
            item(key = "sender-" + q.sender) {
                GlassCard(strong = true, modifier = Modifier.animateItem()) {
                    Text(
                        "Messages from \"${q.sender}\" look like ${q.provider.displayName} (for example ${q.example.display(hidden)}). Is this your bank?",
                        style = MaterialTheme.typography.bodyLarge, color = Kash.colors.text,
                    )
                    Spacer(Modifier.height(Space.s))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        KashButton("Yes, it's my bank", { vm.answerSender(q, true) }, Modifier.weight(1f), icon = Lucide.Check)
                        KashButton("No", { vm.answerSender(q, false) }, style = ButtonStyle.QUIET)
                    }
                }
            }
        }

        ui.scams.firstOrNull()?.let { s ->
            item(key = "scam") {
                Notice(
                    "Possible scam from ${s.sender}: ${s.reasons.first().explanation.lowercase()}. Kash didn't count it as money.",
                    tone = Tone.ERROR,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        ui.gaps.firstOrNull()?.let { g ->
            item(key = "gap") {
                Notice(
                    "${g.amount.display(hidden).removePrefix("−")} left your ${g.account} with no message on this phone explaining it. Usually a deleted SMS, or a charge sent without a message.",
                    tone = Tone.WARNING,
                    modifier = Modifier.animateItem(),
                )
            }
        }

        if (ui.questions.isNotEmpty()) {
            item(key = "q-title") { SectionTitle("Help Kash sort these") }
            items(ui.questions, key = { "q-" + it.txId }) { q ->
                QuestionCard(q, onAnswer = { vm.answer(q, it) }, modifier = Modifier.animateItem())
            }
        } else {
            // All caught up: a line about money, new every day.
            item(key = "quote") { QuoteCard(ui.quote, Modifier.animateItem()) }
        }

        // Older unsorted payments stay unsorted unless the user chooses to deal with them.
        if (ui.olderQuestions.isNotEmpty()) {
            item(key = "older-toggle") {
                val n = ui.olderQuestions.size
                KashButton(
                    if (ui.showOlder) "Done sorting older payments" else "Sort older payments ($n)",
                    { vm.setShowOlder(!ui.showOlder) },
                    style = ButtonStyle.QUIET,
                    icon = if (ui.showOlder) Lucide.Check else Lucide.History,
                    modifier = Modifier.animateItem(),
                )
            }
            if (ui.showOlder) {
                items(ui.olderQuestions, key = { "oq-" + it.txId }) { q ->
                    QuestionCard(q, onAnswer = { vm.answer(q, it) }, modifier = Modifier.animateItem(), showDate = true)
                }
            }
        }

        item(key = "recent-title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Recent", Modifier.weight(1f))
                if (ui.recent.isNotEmpty()) KashButton("See all", onSeeAll, style = ButtonStyle.QUIET, icon = Lucide.ChevronRight)
            }
        }
        if (ui.recent.isNotEmpty()) {
            item(key = "recent") {
                GlassCard(contentPadding = PaddingValues(horizontal = Space.m, vertical = Space.xs)) {
                    ui.recent.forEach { TxRow(it, hidden) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Kash.colors.text,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
private fun Header(ui: HomeUi, hidden: Boolean, onToggleHidden: () -> Unit, loading: Boolean) {
    val c = Kash.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Net worth", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
            if (loading) {
                Skeleton(Modifier.padding(top = Space.xs).width(180.dp).height(36.dp))
            } else {
                Text(
                    if (ui.ready && ui.hasData) ui.netWorth.display(hidden) else "—",
                    style = MaterialTheme.typography.displaySmall,
                    color = c.text,
                    maxLines = 1,
                    modifier = Modifier.semantics { contentDescription = "Net worth " + ui.netWorth.spoken(hidden) },
                )
            }
        }
        Box(
            Modifier.size(Space.touch).pressable(onToggleHidden, CircleShape, onClickLabel = if (hidden) "Show amounts" else "Hide amounts"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (hidden) Lucide.Eye else Lucide.EyeOff, if (hidden) "Show amounts" else "Hide amounts", tint = c.textMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SafeToSpendCard(safe: SafeToSpend.Result, ui: HomeUi, hidden: Boolean) {
    val c = Kash.colors
    val until = ui.nextIncome?.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) ?: ""
    GlassCard(strong = true, modifier = Modifier.animateContentSize()) {
        Text("Safe to spend", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
        Spacer(Modifier.height(Space.xxs))
        Text(
            when (safe.status) {
                SafeToSpend.Status.SHORT -> "Money is short until $until"
                else -> "${safe.perDay.display(hidden)} a day until $until"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = c.text,
        )
        Spacer(Modifier.height(Space.s))
        // Thin, calm progress bar for today's allowance.
        val used = if (safe.perDay.cents <= 0) 1f else (1f - safe.leftToday.cents.toFloat() / safe.perDay.cents).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.skeleton)) {
            Box(Modifier.fillMaxWidth(used).height(4.dp).background(if (used >= 1f) c.negative else c.accent))
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            if (safe.leftToday.cents >= 0) "${safe.leftToday.display(hidden)} left today" else "${(-safe.leftToday).display(hidden)} over today's amount",
            style = MaterialTheme.typography.bodySmall,
            color = c.textMuted,
        )
        if (ui.fuliza.isPositive) {
            Text("Includes ${ui.fuliza.display(hidden)} Fuliza you still owe", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
        Text(
            "Assumes your next money arrives on $until. Setting your own payday is coming soon.",
            style = MaterialTheme.typography.bodySmall,
            color = c.textMuted,
        )
    }
}

@Composable
private fun Accounts(accounts: List<AccountCard>, savings: com.kash.parser.Money, hidden: Boolean, onSetBalance: (AccountCard) -> Unit) {
    val c = Kash.colors
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        items(accounts, key = { it.id }) { a ->
            val real = a.realBalance
            val isBank = a.provider?.isBank == true
            AccountTile(
                if (a.provider?.isMobileMoney == true) Lucide.Smartphone else Lucide.Landmark,
                a.name,
                real?.display(hidden) ?: "Set balance",
                negative = real != null && real.cents < 0,
                note = when {
                    a.owed.isPositive -> "Fuliza owed ${a.owed.display(hidden)}"
                    real == null -> "Messages don't state it"
                    a.estimated -> "Estimated · tap to correct"
                    else -> null
                },
                onClick = if (isBank) ({ onSetBalance(a) }) else null,
            )
        }
        if (savings.isPositive) item(key = "savings") { AccountTile(Lucide.PiggyBank, "Savings", savings.display(hidden)) }
    }
}

@Composable
private fun AccountTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    amount: String,
    negative: Boolean = false,
    note: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = Kash.colors
    GlassCard(
        modifier = Modifier.widthIn(min = 140.dp),
        contentPadding = PaddingValues(Space.m),
        onClick = onClick,
        onClickLabel = if (onClick != null) "Set $name balance" else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = c.textMuted)
            Spacer(Modifier.width(6.dp))
            Text(name, style = MaterialTheme.typography.labelMedium, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(Space.xs))
        Text(amount, style = MaterialTheme.typography.titleMedium, color = if (negative) c.negative else c.text, maxLines = 1)
        if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionCard(q: Question, onAnswer: (Category) -> Unit, modifier: Modifier = Modifier, showDate: Boolean = false) {
    val c = Kash.colors
    var showAll by androidx.compose.runtime.saveable.rememberSaveable(q.txId) { androidx.compose.runtime.mutableStateOf(false) }
    GlassCard(strong = true, modifier = modifier.animateContentSize()) {
        if (showDate && q.at != null) {
            Text(q.at.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            Spacer(Modifier.height(Space.xxs))
        }
        Text(q.prompt, style = MaterialTheme.typography.bodyLarge, color = c.text)
        Spacer(Modifier.height(Space.s))
        // Wraps onto a second line at large font sizes instead of overflowing.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            if (!showAll) {
                q.choices.filter { it != Category.OTHER }.forEach { cat ->
                    Pill(cat.label, selected = false, onClick = { onAnswer(cat) }, icon = cat.icon())
                }
                // "Other…" opens the full list instead of filing it as "Other".
                Pill("Other…", selected = false, onClick = { showAll = true }, icon = Lucide.Ellipsis)
            } else {
                OUTGOING_CATEGORIES.forEach { cat ->
                    Pill(cat.label, selected = false, onClick = { onAnswer(cat) }, icon = cat.icon())
                }
            }
        }
    }
}


/** The daily money line: big and calm, filling the space when there's nothing to sort. */
@Composable
private fun QuoteCard(quote: DailyQuote, modifier: Modifier = Modifier) {
    val c = Kash.colors
    GlassCard(strong = true, modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.l)) {
        Icon(Lucide.Quote, null, Modifier.size(24.dp), tint = c.accent)
        Spacer(Modifier.height(Space.s))
        Text(quote.text, style = MaterialTheme.typography.headlineSmall, color = c.text)
        quote.meaning?.let {
            Spacer(Modifier.height(Space.xs))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
        }
        Spacer(Modifier.height(Space.m))
        Text(quote.source, style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        Text("You're all caught up", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
}

/** For banks whose messages don't state a balance: enter it once; Kash keeps it up to date after that. */
@Composable
private fun SetBalanceDialog(
    account: AccountCard,
    onDismiss: () -> Unit,
    onSave: (com.kash.parser.Money) -> Unit,
    onStopTracking: () -> Unit,
) {
    val c = Kash.colors
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val parsed = com.kash.parser.Money.parseNumber(text.replace(" ", ""))
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.background,
        title = { Text("${account.name} balance", style = MaterialTheme.typography.titleMedium, color = c.text) },
        text = {
            Column {
                Text(
                    "Check your balance in the ${account.name} app or USSD, and enter it here. Kash keeps it up to date from your messages after that.",
                    style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                )
                Spacer(Modifier.height(Space.m))
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' }.take(14) },
                    label = { Text("Balance in KSh") },
                    singleLine = true,
                    isError = text.isNotEmpty() && parsed == null,
                    supportingText = { if (text.isNotEmpty() && parsed == null) Text("Enter an amount like 12,500 or 12500.50") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                )
                Spacer(Modifier.height(Space.s))
                // Not your bank (e.g. a one-off message)? Remove it; it can be brought back in Settings.
                KashButton("Stop tracking ${account.name}", onStopTracking, style = ButtonStyle.QUIET, icon = Lucide.EyeOff)
            }
        },
        confirmButton = { KashButton("Save", { parsed?.let(onSave) }, enabled = parsed != null) },
        dismissButton = { KashButton("Cancel", onDismiss, style = ButtonStyle.QUIET) },
    )
}
