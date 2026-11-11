package com.kash.app

import android.app.Application
import com.kash.app.core.Connectivity
import com.kash.app.core.SettingsStore
import com.kash.app.data.KashRepository
import com.kash.app.data.LocalRepository
import com.kash.app.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** App-wide singletons. Small enough not to need a DI framework. */
class KashApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val repository: KashRepository by lazy { LocalRepository() }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val connectivity: Connectivity by lazy { Connectivity(this) }

    override fun onCreate() {
        super.onCreate()
        // Start reading local data immediately, in the background, so it's ready before the first screen.
        appScope.launch { runCatching { repository.sync() } }
        SyncWorker.schedule(this)
    }
}
