package com.kash.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kash.app.ui.icons.Lucide
import com.kash.app.ui.theme.Kash
import com.kash.app.ui.theme.Space

enum class ButtonStyle { PRIMARY, SECONDARY, QUIET }

/**
 * At least 48dp tall. When [busy], it shows a small inline progress ring right away and ignores
 * repeat taps, but never greys out or stops responding to touch ("no frozen buttons").
 * Work behind the click must run off the main thread (ViewModel coroutines).
 */
@Composable
fun KashButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    icon: ImageVector? = null,
    busy: Boolean = false,
    enabled: Boolean = true,
) {
    val c = Kash.colors
    val shape = RoundedCornerShape(14.dp)
    val (bg, fg) = when (style) {
        ButtonStyle.PRIMARY -> c.accent to c.onAccent
        ButtonStyle.SECONDARY -> c.glassStrong to c.text
        ButtonStyle.QUIET -> androidx.compose.ui.graphics.Color.Transparent to c.accentText
    }
    Row(
        modifier
            .heightIn(min = Space.touch)
            .pressable(onClick = { if (!busy) onClick() }, shape = shape, enabled = enabled)
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .then(if (style == ButtonStyle.SECONDARY) Modifier.border(1.dp, c.border, shape) else Modifier)
            .padding(horizontal = Space.l, vertical = Space.s),
        horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            busy -> CircularProgressIndicator(Modifier.size(16.dp), color = fg, strokeWidth = 2.dp)
            icon != null -> Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Small selectable pill (filters, theme choice). 48dp touch height even though it looks compact. */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val c = Kash.colors
    val shape = RoundedCornerShape(50)
    Box(modifier.heightIn(min = Space.touch), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .pressable(onClick, shape, role = Role.Tab)
                .background(if (selected) c.accent else c.glass)
                .border(1.dp, if (selected) c.accent else c.border, shape)
                .padding(horizontal = Space.m, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = if (selected) c.onAccent else c.textMuted)
            Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) c.onAccent else c.text, maxLines = 1)
        }
    }
}

/** Plain icon on a faint circle. Used for list rows; deliberately not a coloured rounded square. */
@Composable
fun IconDot(icon: ImageVector, modifier: Modifier = Modifier, tint: androidx.compose.ui.graphics.Color = Kash.colors.text) {
    val c = Kash.colors
    Box(
        modifier.size(36.dp).background(c.glassStrong, CircleShape).border(1.dp, c.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }
}

/** Search box: results update on every keystroke (filtering happens on local data, so it's instant). */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val c = Kash.colors
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .heightIn(min = Space.touch)
            .background(c.glassStrong, shape)
            .border(1.dp, c.border, shape)
            .padding(start = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Search, null, Modifier.size(18.dp), tint = c.textMuted)
        Box(Modifier.weight(1f).padding(horizontal = Space.s)) {
            if (query.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = c.textMuted, maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // The search key just closes the keyboard; results are already filtered as you type.
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus() }),
                // Whole width is tappable, not only the few pixels of text already typed.
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
        AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.size(Space.touch).pressable({ onQueryChange("") }, CircleShape, onClickLabel = "Clear search"),
                contentAlignment = Alignment.Center,
            ) { Icon(Lucide.X, "Clear search", Modifier.size(18.dp), tint = c.textMuted) }
        }
    }
}
