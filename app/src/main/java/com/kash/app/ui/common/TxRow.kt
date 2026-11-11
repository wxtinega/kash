package com.kash.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kash.app.data.TxItem
import com.kash.app.ui.components.IconDot
import com.kash.app.ui.display
import com.kash.app.ui.icon
import com.kash.app.ui.icons.Lucide
import com.kash.app.ui.shortTime
import com.kash.app.ui.spoken
import com.kash.app.ui.theme.AmountStyle
import com.kash.app.ui.theme.Kash
import com.kash.app.ui.theme.Space

/**
 * One transaction. The title wraps to two lines and the amount never gets squeezed:
 * the text column takes the remaining width (weight), so large fonts don't overlap.
 */
@Composable
fun TxRow(tx: TxItem, hidden: Boolean, modifier: Modifier = Modifier) {
    val c = Kash.colors
    val categoryLabel = tx.category?.label ?: "Not sorted yet"
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = Space.s)
            .clearAndSetSemantics {
                contentDescription = "${tx.title}, $categoryLabel, ${tx.amount.spoken(hidden, tx.incoming)}" +
                    (if (tx.fee.isPositive) ", plus ${tx.fee.spoken(hidden)} transaction cost" else "") + ", ${tx.at.shortTime()}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconDot(tx.category?.icon() ?: Lucide.CircleHelp, tint = if (tx.category == null) c.accentText else c.text)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(tx.title, style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "$categoryLabel · ${tx.at.shortTime()}" +
                    (if (tx.fee.isPositive) " · ${tx.fee.display(hidden)} cost" else "") +
                    (if (tx.pending) " · Processing" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.s))
        Text(
            tx.amount.display(hidden, sign = true, incoming = tx.incoming),
            style = AmountStyle,
            color = if (tx.incoming) c.positive else c.text,
            maxLines = 1,
        )
    }
}

