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
import com.kash.app.ui.KashNav
import com.kash.app.ui.lock.LockScreen
import com.kash.app.ui.theme.KashTheme

/** FragmentActivity because the biometric prompt needs it. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash shows only while the process starts; no artificial delay.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        // Block screenshots and hide content in the recent-apps view.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val app = application as KashApp
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
            var unlocked by rememberSaveable { mutableStateOf(false) }
            KashTheme(mode = settings.themeMode) {
                AnimatedContent(
                    targetState = unlocked,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "lock",
                ) { open ->
                    if (open) KashNav(app, settings) else LockScreen(onUnlocked = { unlocked = true })
                }
            }
        }
    }
}
