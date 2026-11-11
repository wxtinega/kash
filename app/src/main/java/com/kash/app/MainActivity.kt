package com.kash.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kash.app.core.UserSettings
import androidx.compose.runtime.rememberCoroutineScope
import com.kash.app.data.SmsInbox
import com.kash.app.ui.KashNav
import com.kash.app.ui.permissions.SmsAccessScreen
import com.kash.app.ui.components.KashBackground
import com.kash.app.ui.consent.ConsentScreen
import com.kash.app.ui.consent.PRIVACY_POLICY_VERSION
import kotlinx.coroutines.launch
import com.kash.app.ui.lock.LockScreen
import com.kash.app.ui.theme.KashTheme

private enum class Stage { LOADING, CONSENT, LOCKED, SMS_ACCESS, OPEN }

/** FragmentActivity because the biometric prompt needs it. */
class MainActivity : FragmentActivity() {
    /** Re-checked on every resume: the user may have granted SMS access in App info meanwhile. */
    private val smsGranted = mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        smsGranted.value = SmsInbox.hasPermission(this)
        // Pick up anything that arrived while Kash was in the background.
        val app = application as KashApp
        app.appScope.launch { runCatching { app.repository.sync() } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash shows only while the process starts; no artificial delay.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        smsGranted.value = SmsInbox.hasPermission(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        // Block screenshots and hide content in the recent-apps view.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val app = application as KashApp
        setContent {
            // null until stored settings are read (a few ms), so the consent screen never flashes for people who agreed.
            val loaded by app.settings.settings.collectAsStateWithLifecycle<UserSettings?>(initialValue = null)
            val settings = loaded ?: UserSettings()
            var unlocked by rememberSaveable { mutableStateOf(false) }
            var skippedSms by rememberSaveable { mutableStateOf(false) }
            val hasSms by smsGranted
            val scope = rememberCoroutineScope()
            KashTheme(mode = settings.themeMode) {
                val stage = when {
                    loaded == null -> Stage.LOADING
                    settings.acceptedPolicyVersion < PRIVACY_POLICY_VERSION -> Stage.CONSENT
                    !unlocked -> Stage.LOCKED
                    !hasSms && !skippedSms -> Stage.SMS_ACCESS
                    else -> Stage.OPEN
                }
                AnimatedContent(
                    targetState = stage,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "stage",
                ) { s ->
                    when (s) {
                        Stage.LOADING -> KashBackground {}
                        Stage.CONSENT -> ConsentScreen(onAgree = {
                            scope.launch { app.settings.acceptPolicy(PRIVACY_POLICY_VERSION) }
                        })
                        Stage.LOCKED -> LockScreen(onUnlocked = { unlocked = true })
                        Stage.SMS_ACCESS -> SmsAccessScreen(
                            onGranted = {
                                smsGranted.value = true
                                app.appScope.launch { runCatching { app.repository.sync() } }
                            },
                            onSkip = { skippedSms = true },
                        )
                        Stage.OPEN -> KashNav(app, settings, onRequestSms = { skippedSms = false })
                    }
                }
            }
        }
    }
}
