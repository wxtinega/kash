package com.kash.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kash.app.core.UserSettings
import com.kash.app.ui.components.GlassCard
import com.kash.app.ui.components.IconDot
import com.kash.app.ui.components.Notice
import com.kash.app.ui.components.Pill
import com.kash.app.ui.components.Tone
import com.kash.app.ui.components.pressable
import com.kash.app.ui.icons.Lucide
import com.kash.app.ui.theme.Kash
import com.kash.app.ui.theme.Space
import com.kash.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    online: Flow<Boolean>,
    seenProviders: List<com.kash.parser.Provider>,
    ignoredProviders: Set<com.kash.parser.Provider>,
    onTracked: (com.kash.parser.Provider, Boolean) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onHideAmounts: (Boolean) -> Unit,
    contentPadding: PaddingValues,
) {
    val c = Kash.colors
    val isOnline by online.collectAsStateWithLifecycle(initialValue = true)

    LazyColumn(
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen,
            top = contentPadding.calculateTopPadding() + Space.s,
            bottom = contentPadding.calculateBottomPadding() + Space.l,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading() })
        }
        item {
            GlassCard {
                Text("Appearance", style = MaterialTheme.typography.titleSmall, color = c.text)
                Text("Follows your phone's setting unless you choose one.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                Spacer(Modifier.height(Space.s))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Pill("System", settings.themeMode == ThemeMode.SYSTEM, { onTheme(ThemeMode.SYSTEM) }, icon = Lucide.Monitor)
                    Pill("Light", settings.themeMode == ThemeMode.LIGHT, { onTheme(ThemeMode.LIGHT) }, icon = Lucide.Sun)
                    Pill("Dark", settings.themeMode == ThemeMode.DARK, { onTheme(ThemeMode.DARK) }, icon = Lucide.Moon)
                }
            }
        }
        item {
            GlassCard(contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.s)) {
                Text("Accounts", style = MaterialTheme.typography.titleSmall, color = c.text)
                Text(
                    if (seenProviders.isEmpty()) "Accounts appear here once Kash finds messages from them."
                    else "Kash only shows accounts it found messages from. Switch off any you don't want tracked; their messages will be ignored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                )
                seenProviders.forEach { p ->
                    ToggleRow(
                        icon = if (p.isMobileMoney) Lucide.Smartphone else Lucide.Landmark,
                        title = p.displayName,
                        body = if (p in ignoredProviders) "Not tracked" else "Tracked",
                        checked = p !in ignoredProviders,
                        onChange = { onTracked(p, it) },
                    )
                }
            }
        }
        item {
            GlassCard(contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
                ToggleRow(
                    icon = Lucide.EyeOff,
                    title = "Hide amounts",
                    body = "Show KSh •••• instead of figures, for checking Kash in public.",
                    checked = settings.hideAmounts,
                    onChange = onHideAmounts,
                )
            }
        }
        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconDot(Lucide.Lock)
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text("Private by design", style = MaterialTheme.typography.titleSmall, color = c.text)
                        Text(
                            "Your messages are read and stored only on this phone. Nothing is uploaded. Privacy policy version 1, agreed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textMuted,
                        )
                    }
                }
            }
        }
        if (!isOnline) {
            item {
                Notice(
                    "You're offline. Everything in Kash still works; backup will resume when you reconnect.",
                    tone = Tone.INFO,
                    icon = Lucide.WifiOff,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val c = Kash.colors
    // The whole row is the touch target, not just the small switch.
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touch)
            .pressable({ onChange(!checked) }, role = Role.Switch)
            .semantics { toggleableState = if (checked) ToggleableState.On else ToggleableState.Off },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconDot(icon)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f).padding(vertical = Space.s)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
            Text(body, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = null, // row handles the tap
            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent),
        )
    }
}
