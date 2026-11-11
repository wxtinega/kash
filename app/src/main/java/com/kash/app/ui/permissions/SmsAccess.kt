package com.kash.app.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.core.app.ActivityCompat
import com.kash.app.ui.components.ButtonStyle
import com.kash.app.ui.components.GlassCard
import com.kash.app.ui.components.IconDot
import com.kash.app.ui.components.KashBackground
import com.kash.app.ui.components.KashButton
import com.kash.app.ui.components.Notice
import com.kash.app.ui.components.Tone
import com.kash.app.ui.icons.Lucide
import com.kash.app.ui.theme.Kash
import com.kash.app.ui.theme.Space

/** SMS (required) plus notifications on Android 13+ (for "What was this?" and the monthly summary). */
val SMS_PERMISSIONS: Array<String> = buildList {
    add(Manifest.permission.READ_SMS)
    add(Manifest.permission.RECEIVE_SMS)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

/** Opens Kash's App info page, where the user can grant the permission or allow restricted settings. */
fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/**
 * Asks for SMS access with an explanation first. If Android blocks it (user chose "Don't allow"
 * twice, or "restricted settings" for apps installed outside the Play Store), it explains how to
 * allow it in App info instead of silently failing.
 */
@Composable
fun SmsAccessScreen(onGranted: () -> Unit, onSkip: () -> Unit) {
    val c = Kash.colors
    val context = LocalContext.current
    var blocked by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.READ_SMS] == true) {
            onGranted()
        } else {
            val activity = context as? Activity
            // No rationale after a denial = Android won't show the prompt again (or it's restricted).
            blocked = activity == null || !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_SMS)
        }
    }

    KashBackground {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Spacer(Modifier.height(Space.l))
            Text("Connect your money messages", style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading() })
            Text(
                "Kash builds your balances and spending from the M-Pesa and bank messages already on your phone. No typing, no bank logins.",
                style = MaterialTheme.typography.bodyLarge,
                color = c.textMuted,
            )
            GlassCard(strong = true) {
                Point(Lucide.Smartphone, "Reads only money messages", "M-Pesa, banks and savings providers. Other messages are checked for scams, never stored.")
                Spacer(Modifier.height(Space.m))
                Point(Lucide.Lock, "Stays on this phone", "Nothing is uploaded or shared, with anyone.")
                Spacer(Modifier.height(Space.m))
                Point(Lucide.Bell, "Notifications (optional)", "Quick questions like \"What was this payment?\" and your monthly summary.")
            }
            if (blocked) {
                Notice(
                    "Android blocked the request. Open App info and allow SMS. If it's greyed out: tap the three-dot menu at the top of App info, choose \"Allow restricted settings\", then allow SMS.",
                    tone = Tone.WARNING,
                )
                KashButton("Open App info", { openAppSettings(context) }, Modifier.fillMaxWidth(), icon = Lucide.Settings)
                KashButton("I've allowed it", { if (com.kash.app.data.SmsInbox.hasPermission(context)) onGranted() else blocked = true }, Modifier.fillMaxWidth(), style = ButtonStyle.SECONDARY)
            } else {
                KashButton("Allow access to messages", { launcher.launch(SMS_PERMISSIONS) }, Modifier.fillMaxWidth(), icon = Lucide.Check)
            }
            KashButton("Not now", onSkip, Modifier.fillMaxWidth(), style = ButtonStyle.QUIET)
        }
    }
}

@Composable
private fun Point(icon: ImageVector, title: String, body: String) {
    val c = Kash.colors
    Row(verticalAlignment = Alignment.Top) {
        IconDot(icon)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
            Text(body, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
    }
}
