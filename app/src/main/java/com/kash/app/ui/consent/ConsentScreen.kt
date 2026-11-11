package com.kash.app.ui.consent

import android.app.Activity
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kash.app.ui.components.ButtonStyle
import com.kash.app.ui.components.KashBackground
import com.kash.app.ui.components.KashButton
import com.kash.app.ui.components.Notice
import com.kash.app.ui.components.Tone
import com.kash.app.ui.components.pressable
import com.kash.app.ui.icons.Lucide
import com.kash.app.ui.theme.Kash
import com.kash.app.ui.theme.Space

/** Bump when legal/PRIVACY.md changes in a way users must re-agree to. */
const val PRIVACY_POLICY_VERSION = 2

/**
 * Shown before anything else until the user agrees to the current policy version.
 * The text is the same file published in the repository (legal/PRIVACY.md), bundled into the app,
 * so it reads offline and can never drift from the public copy.
 */
@Composable
fun ConsentScreen(onAgree: () -> Unit) {
    val c = Kash.colors
    val context = LocalContext.current
    val blocks = remember { PolicyText.load(context) }
    var ticked by rememberSaveable { mutableStateOf(false) }
    var nudge by rememberSaveable { mutableStateOf(false) }
    var declined by rememberSaveable { mutableStateOf(false) }

    KashBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.padding(horizontal = Space.screen, vertical = Space.m)) {
                Text("Before you start", style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading() })
                Text(
                    "Kash reads your money messages to work. Here's exactly what it does with them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textMuted,
                )
            }

            // The full policy, scrollable, in a crisp (strong) glass card for readability.
            val shape = RoundedCornerShape(16.dp)
            LazyColumn(
                Modifier
                    .weight(1f)
                    .padding(horizontal = Space.screen)
                    .background(c.glassStrong, shape)
                    .border(1.dp, c.border, shape),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.l),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                items(blocks) { b -> PolicyBlockView(b) }
            }

            Column(Modifier.padding(Space.screen), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                // Whole row is the touch target.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Space.touch)
                        .pressable({ ticked = !ticked; nudge = false }, RoundedCornerShape(12.dp), role = Role.Checkbox)
                        .semantics { toggleableState = if (ticked) ToggleableState.On else ToggleableState.Off }
                        .padding(horizontal = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckBox(ticked)
                    Spacer(Modifier.width(Space.m))
                    Text(
                        "I've read the privacy policy and agree to it. I'm 18 or older.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.text,
                    )
                }
                AnimatedVisibility(nudge) { Notice("Tick the box above to confirm you agree.", tone = Tone.WARNING) }
                AnimatedVisibility(declined) {
                    Notice(
                        "Kash can't work without your agreement, because reading your messages is how it tracks your money. You can come back any time.",
                        tone = Tone.INFO,
                        actionLabel = "Close Kash",
                        onAction = { (context as? Activity)?.finishAffinity() },
                    )
                }
                KashButton(
                    "Agree and continue",
                    onClick = { if (ticked) onAgree() else nudge = true },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Lucide.Check,
                )
                KashButton("Decline", { declined = true }, modifier = Modifier.fillMaxWidth(), style = ButtonStyle.QUIET)
            }
        }
    }
}

@Composable
private fun CheckBox(checked: Boolean) {
    val c = Kash.colors
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(24.dp)
            .background(if (checked) c.accent else c.glass, shape)
            .border(1.5.dp, if (checked) c.accent else c.textMuted, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Lucide.Check, null, Modifier.size(16.dp), tint = c.onAccent)
    }
}

/** Minimal Markdown subset used by PRIVACY.md: #/## headings, bullets, tables, **bold**, paragraphs. */
sealed interface PolicyBlock {
    data class Title(val text: String) : PolicyBlock
    data class Heading(val text: String) : PolicyBlock
    data class Paragraph(val text: String) : PolicyBlock
    data class Bullet(val text: String) : PolicyBlock
}

object PolicyText {
    fun load(context: Context): List<PolicyBlock> =
        runCatching { context.assets.open("PRIVACY.md").bufferedReader().use { parse(it.readText()) } }
            .getOrElse { listOf(PolicyBlock.Paragraph("The privacy policy couldn't be loaded. Please reinstall Kash.")) }

    fun parse(md: String): List<PolicyBlock> {
        val out = mutableListOf<PolicyBlock>()
        val para = StringBuilder()
        var tableHeaderSeen = false
        fun flush() { if (para.isNotBlank()) out += PolicyBlock.Paragraph(para.toString().trim()); para.clear() }
        for (raw in md.lines()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> { flush(); tableHeaderSeen = false }
                line.startsWith("## ") -> { flush(); out += PolicyBlock.Heading(line.removePrefix("## ")) }
                line.startsWith("# ") -> { flush(); out += PolicyBlock.Title(line.removePrefix("# ")) }
                line.startsWith("- ") -> { flush(); out += PolicyBlock.Bullet(line.removePrefix("- ")) }
                line.startsWith("|") -> {
                    flush()
                    val cells = line.trim('|').split('|').map { it.trim() }
                    when {
                        cells.all { it.matches(Regex("-+")) } -> Unit
                        !tableHeaderSeen -> tableHeaderSeen = true
                        else -> out += PolicyBlock.Bullet(cells.joinToString(": ") { it })
                    }
                }
                else -> { if (para.isNotEmpty()) para.append(' '); para.append(line) }
            }
        }
        flush()
        return out
    }
}

/** Turns **bold** into real bold. */
private fun rich(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { i, part -> if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) } else append(part) }
}

@Composable
private fun PolicyBlockView(b: PolicyBlock) {
    val c = Kash.colors
    when (b) {
        is PolicyBlock.Title -> Text(b.text, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.semantics { heading() })
        is PolicyBlock.Heading -> Text(
            b.text,
            style = MaterialTheme.typography.titleSmall,
            color = c.text,
            modifier = Modifier.padding(top = Space.m).semantics { heading() },
        )
        is PolicyBlock.Paragraph -> Text(rich(b.text), style = MaterialTheme.typography.bodyMedium, color = c.text)
        is PolicyBlock.Bullet -> Row {
            Text("•", style = MaterialTheme.typography.bodyMedium, color = c.accentText, modifier = Modifier.width(16.dp))
            Text(rich(b.text), style = MaterialTheme.typography.bodyMedium, color = c.text)
        }
    }
}
