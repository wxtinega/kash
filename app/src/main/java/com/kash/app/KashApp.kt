package com.kash.app

import android.app.Application
import com.kash.app.core.Connectivity
import com.kash.app.core.SettingsStore
import com.kash.app.data.KashRepository
import com.kash.app.data.KashPrefs
import com.kash.app.data.LocalRepository
import com.kash.app.data.PrefsLearnedRules
import com.kash.app.data.SmsInbox
import com.kash.app.sync.MonthlySummaryWorker
import com.kash.app.sync.SyncWorker
import com.kash.app.sync.TransactionNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** App-wide singletons. Small enough not to need a DI framework. */
class KashApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val repository: KashRepository by lazy {
        LocalRepository(
            messages = { SmsInbox.read(this) },
            hasAccess = { SmsInbox.hasPermission(this) },
            learned = PrefsLearnedRules(this),
            prefs = prefs,
        )
    }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val connectivity: Connectivity by lazy { Connectivity(this) }
    val prefs: KashPrefs by lazy { KashPrefs(this) }

    /** True while Kash is on screen: new payments then show as in-app cards, not notifications. */
    @Volatile var onScreen: Boolean = false

    override fun onCreate() {
        super.onCreate()
        // Start reading messages immediately, in the background, so data is ready before the first screen.
        appScope.launch { runCatching { repository.sync() } }
        // Every time messages are re-read (new SMS, background sync, app open), ask about new payments.
        appScope.launch {
            repository.state.collect { state ->
                if (state.ready) runCatching { TransactionNotifier.onState(this@KashApp, state, prefs, onScreen) }
            }
        }
        SyncWorker.schedule(this)
        MonthlySummaryWorker.schedule(this)
    }
}
