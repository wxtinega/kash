package com.kash.app.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.kash.app.ui.components.KashBackground
import com.kash.app.ui.components.KashButton
import com.kash.app.ui.components.Notice
import com.kash.app.ui.components.Tone
import com.kash.app.ui.icons.Lucide
import com.kash.app.ui.theme.Kash
import com.kash.app.ui.theme.Space

/**
 * First screen on every open. Opens the fingerprint / face / PIN prompt immediately (no extra tap).
 * Phones with no screen lock set up can continue, with a nudge to add one.
 * The small static "made by T.I" sits at the bottom.
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val c = Kash.colors
    val activity = LocalContext.current as FragmentActivity
    var error by remember { mutableStateOf<String?>(null) }
    val canAuth = remember {
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun prompt() {
        if (!canAuth) { onUnlocked(); return }
        val p = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { error = null; onUnlocked() }
                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    error = when (code) {
                        BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_CANCELED -> null
                        BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "Too many tries. Use your phone's PIN, or wait a moment and try again."
                        else -> "Couldn't check it's you. Please try again."
                    }
                }
            },
        )
        p.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Kash")
                .setSubtitle("Your money stays private")
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build(),
        )
    }

    LaunchedEffect(Unit) { prompt() }

    KashBackground {
        Column(
            Modifier.fillMaxSize().padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Kash", style = MaterialTheme.typography.displaySmall, color = c.text)
            Spacer(Modifier.height(Space.xs))
            Text("Your money, clearly.", style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
            Spacer(Modifier.height(Space.xl))
            KashButton(if (canAuth) "Unlock" else "Continue", { prompt() }, icon = if (canAuth) Lucide.Fingerprint else Lucide.Lock)
            if (!canAuth) {
                Spacer(Modifier.height(Space.m))
                Notice("Tip: set a screen lock on your phone so only you can open Kash.", tone = Tone.WARNING)
            }
            error?.let {
                Spacer(Modifier.height(Space.m))
                Notice(it, tone = Tone.ERROR)
            }
        }
        Text(
            "made by T.I",
            style = MaterialTheme.typography.labelSmall,
            color = c.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = Space.l),
        )
    }
}
