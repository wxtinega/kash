package com.kash.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kash.app.ui.theme.Kash
import com.kash.app.ui.theme.Space

/**
 * Screen backdrop: the theme background plus one faint accent glow in the top corner.
 * The glass cards sit over it, which is what makes the translucency read as layered.
 * No backdrop blur (it would cost frames and readability).
 */
@Composable
fun KashBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = Kash.colors
    Box(
        modifier
            .fillMaxSize()
            .background(c.background)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c.backdropTint, Color.Transparent),
                        center = Offset(size.width * 0.9f, 0f),
                        radius = size.width * 0.9f,
                    ),
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.9f, 0f),
                )
            },
        content = content,
    )
}

/**
 * Translucent card with a hairline border. [strong] = more opaque, for content that must stay
 * crisp over busy areas (e.g. questions, alerts).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(Space.l),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Kash.colors
    val shape = MaterialTheme.shapes.medium
    val base = if (onClick != null) modifier.pressable(onClick, shape, onClickLabel = onClickLabel) else modifier.clip(shape)
    Column(
        base
            .background(if (strong) c.glassStrong else c.glass)
            .border(1.dp, c.border, shape)
            .padding(contentPadding),
        content = content,
    )
}
