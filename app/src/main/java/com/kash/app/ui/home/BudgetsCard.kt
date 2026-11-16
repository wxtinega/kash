package com.kash.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kash.app.ui.components.ButtonStyle
import com.kash.app.ui.components.GlassCard
import com.kash.app.ui.components.KashButton
import com.kash.app.ui.components.Pill
import com.kash.app.ui.components.pressable
import com.kash.app.ui.display
import com.kash.app.ui.icon
import com.kash.app.ui.icons.Lucide
import com.kash.app.ui.theme.Kash
import com.kash.app.ui.theme.KashColors
import com.kash.app.ui.theme.Space
import com.kash.insights.BudgetLevel
import com.kash.insights.BudgetStatus
import com.kash.insights.Budgets
import com.kash.insights.Category
import com.kash.parser.Money

/** Which budget the editor is open for. [category] null with [isNew] false = "All spending". */
data class BudgetEdit(val isNew: Boolean, val category: Category? = null, val current: Money? = null)

fun KashColors.forLevel(level: BudgetLevel): Color = when (level) {
    BudgetLevel.GREEN -> positive
    BudgetLevel.ORANGE -> warning
    BudgetLevel.RED, BudgetLevel.OVER -> negative
}

/**
 * The Budgets card on Home. Each budget: how much is used (bar + percent + a word, so it never
 * depends on colour alone), what's spent of the limit, and what can still be spent per day.
 */
@Composable
fun BudgetsCard(
    budgets: List<BudgetStatus>,
    hidden: Boolean,
    onEdit: (BudgetEdit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Kash.colors
    GlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Target, null, Modifier.size(18.dp), tint = c.accentText)
            Spacer(Modifier.width(Space.s))
            Text("Budgets this month", style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f).semantics { heading() })
        }
        if (budgets.isEmpty()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                "Set how much you want to spend on things like food or transport. Kash shows how much is left and what you can spend each day.",
                style = MaterialTheme.typography.bodySmall, color = c.textMuted,
            )
        }
        budgets.forEach { s ->
            Spacer(Modifier.height(Space.s))
            BudgetRow(s, hidden, onClick = { onEdit(BudgetEdit(false, s.budget.category, s.budget.limit)) })
        }
        Spacer(Modifier.height(Space.s))
        KashButton(
            if (budgets.isEmpty()) "Set a budget" else "Add a budget",
            { onEdit(BudgetEdit(true)) },
            style = if (budgets.isEmpty()) ButtonStyle.SECONDARY else ButtonStyle.QUIET,
            icon = Lucide.Plus,
        )
    }
}

@Composable
private fun BudgetRow(s: BudgetStatus, hidden: Boolean, onClick: () -> Unit) {
    val c = Kash.colors
    val color = c.forLevel(s.level)
    val levelIcon = when (s.level) {
        BudgetLevel.GREEN -> Lucide.Check
        BudgetLevel.ORANGE -> Lucide.Percent
        BudgetLevel.RED, BudgetLevel.OVER -> Lucide.TriangleAlert
    }
    val detail = when {
        s.level == BudgetLevel.OVER -> "Over by ${(Money.ZERO - s.left).display(hidden)}"
        s.daysLeft <= 0 -> "${s.left.display(hidden)} left"
        else -> "${s.perDay.display(hidden)} a day for ${s.daysLeft} day${if (s.daysLeft == 1) "" else "s"}"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touch)
            .pressable(onClick, RoundedCornerShape(10.dp), onClickLabel = "Change ${s.budget.label} budget")
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${s.budget.label}: ${s.percent} percent used, ${s.level.word}. " +
                    "${if (hidden) "Amounts hidden" else "${s.spent.format()} of ${s.budget.limit.format()}"}. $detail."
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(s.budget.category?.icon() ?: Lucide.Wallet, null, Modifier.size(16.dp), tint = c.textMuted)
            Spacer(Modifier.width(Space.s))
            Text(s.budget.label, style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(levelIcon, null, Modifier.size(14.dp), tint = color)
            Spacer(Modifier.width(4.dp))
            Text("${s.percent}% · ${s.level.word}", style = MaterialTheme.typography.labelMedium, color = c.text)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.skeleton)) {
            Box(
                Modifier
                    .fillMaxWidth((s.percent / 100f).coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row {
            Text(
                "${s.spent.display(hidden)} of ${s.budget.limit.display(hidden)}",
                style = MaterialTheme.typography.bodySmall, color = c.textMuted, modifier = Modifier.weight(1f),
            )
            Text(detail, style = MaterialTheme.typography.bodySmall, color = if (s.level == BudgetLevel.OVER) c.negative else c.text)
        }
    }
}

/**
 * Add or change a budget. New: pick a category, then an amount (suggested from last month).
 * Existing: change the amount or remove it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetEditorDialog(
    edit: BudgetEdit,
    taken: Set<Category?>,
    suggestions: Map<Category?, Money>,
    onSave: (Category?, Money) -> Unit,
    onRemove: (Category?) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Kash.colors
    // For a new budget, start with the first category that doesn't have one yet.
    val choices: List<Category?> = (listOf<Category?>(null) + Budgets.budgetable).filter { !edit.isNew || it !in taken }
    var category by remember { mutableStateOf(if (edit.isNew) choices.firstOrNull { it != null } ?: choices.firstOrNull() else edit.category) }
    fun prefill(cat: Category?): String = (if (edit.isNew) suggestions[cat] else edit.current)?.let { m -> (m.cents / 100).toString() } ?: ""
    var text by remember { mutableStateOf(prefill(category)) }
    var touched by remember { mutableStateOf(false) }
    val parsed = Money.parseNumber(text.replace(" ", ""))?.takeIf { it.isPositive }
    val label = category?.label ?: "All spending"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.background,
        title = { Text(if (edit.isNew) "New budget" else "$label budget", style = MaterialTheme.typography.titleMedium, color = c.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (edit.isNew) {
                    if (choices.isEmpty()) {
                        Text("Every category already has a budget.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    } else {
                        Text("What for?", style = MaterialTheme.typography.labelLarge, color = c.text)
                        Spacer(Modifier.height(Space.xs))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                            choices.forEach { cat ->
                                Pill(cat?.label ?: "All spending", category == cat, {
                                    category = cat
                                    if (!touched) text = prefill(cat) // follow the suggestion until the user types
                                })
                            }
                        }
                        Spacer(Modifier.height(Space.m))
                    }
                }
                if (!edit.isNew || choices.isNotEmpty()) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v -> touched = true; text = v.filter { it.isDigit() || it == ',' }.take(10) },
                        label = { Text("$label, per month (KSh)") },
                        singleLine = true,
                        isError = text.isNotEmpty() && parsed == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val hint = suggestions[category]?.let { "Last month you spent about ${it.format()} on ${label.lowercase()}." }
                    if (hint != null) {
                        Spacer(Modifier.height(Space.xxs))
                        Text(hint, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                    if (!edit.isNew) {
                        Spacer(Modifier.height(Space.s))
                        KashButton("Remove this budget", { onRemove(edit.category) }, style = ButtonStyle.QUIET, icon = Lucide.Trash2)
                    }
                }
            }
        },
        confirmButton = {
            KashButton("Save", { parsed?.let { onSave(category, it) } }, enabled = parsed != null && (!edit.isNew || choices.isNotEmpty()))
        },
        dismissButton = { KashButton("Cancel", onDismiss, style = ButtonStyle.QUIET) },
    )
}
