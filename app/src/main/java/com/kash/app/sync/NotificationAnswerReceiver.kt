package com.kash.app.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.kash.app.KashApp
import com.kash.insights.Category
import kotlinx.coroutines.launch

/** A quick-answer button on a "What was it for?" notification. Not exported: only Kash's own notification can call it. */
class NotificationAnswerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val txId = intent.getStringExtra(EXTRA_TX)?.take(64) ?: return
        val category = intent.getStringExtra(EXTRA_CATEGORY)?.let { runCatching { Category.valueOf(it) }.getOrNull() } ?: return
        NotificationManagerCompat.from(context).cancel(TransactionNotifier.id(txId))
        val app = context.applicationContext as KashApp
        val pending = goAsync()
        app.appScope.launch {
            try { app.repository.answer(txId, category) } catch (_: Throwable) { } finally { pending.finish() }
        }
    }

    companion object {
        const val EXTRA_TX = "kash.tx"
        const val EXTRA_CATEGORY = "kash.category"
    }
}
