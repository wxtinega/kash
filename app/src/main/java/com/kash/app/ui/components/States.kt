package com.kash.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kash.app.ui.icons.Lucide
import com.kash.app.ui.theme.Kash
import com.kash.app.ui.theme.Space
import kotlinx.coroutines.delay

/**
 * Skeletons only appear if loading takes longer than [delayMs]. Local data usually arrives in a
 * few milliseconds, so most of the time the user never sees a loading state at all.
 */
@Composable
fun rememberShowLoading(loading: Boolean, delayMs: Long = 150): Boolean {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (loading) { delay(delayMs); show = true } else show = false
    }
    return show && loading
}

/** Gently pulsing placeholder in the shape of the content to come. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Box(modifier.graphicsLayer { alpha = a }.background(Kash.colors.skeleton, shape))
}

/** A transaction row's skeleton: same height as the real row, so nothing jumps when data lands. */
@Composable
fun SkeletonRow() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.s).clearAndSetSemantics { contentDescription = "Loading" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Skeleton(Modifier.size(36.dp), CircleShape)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Skeleton(Modifier.fillMaxWidth(0.55f).height(14.dp))
            Skeleton(Modifier.fillMaxWidth(0.35f).height(11.dp))
        }
        Skeleton(Modifier.width(72.dp).height(14.dp))
    }
}

/** Every list has a real empty state: what this is, why it's empty, and what to do. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = Kash.colors
    Column(
        modifier.fillMaxWidth().padding(vertical = Space.xl, horizontal = Space.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Box(
            Modifier.size(52.dp).background(c.glassStrong, CircleShape).border(1.dp, c.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(22.dp), tint = c.textMuted) }
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.xs))
            KashButton(actionLabel, onAction, style = ButtonStyle.SECONDARY)
        }
    }
}

enum class Tone { INFO, WARNING, ERROR }

/** Inline, human-readable notice (offline, errors, scam warnings). Never a raw exception. */
@Composable
fun Notice(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.INFO,
    icon: ImageVector = when (tone) {
        Tone.INFO -> Lucide.CircleHelp
        Tone.WARNING -> Lucide.TriangleAlert
        Tone.ERROR -> Lucide.ShieldAlert
    },
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = Kash.colors
    val tint = when (tone) {
        Tone.INFO -> c.textMuted
        Tone.WARNING -> c.warning
        Tone.ERROR -> c.negative
    }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .background(c.glassStrong, shape)
            .border(1.dp, c.border, shape)
            .padding(start = Space.m, end = Space.xs, top = Space.xs, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = c.text,
            modifier = Modifier.weight(1f).padding(horizontal = Space.s, vertical = Space.s),
        )
        if (actionLabel != null && onAction != null) {
            KashButton(actionLabel, onAction, style = ButtonStyle.QUIET)
        }
    }
}
