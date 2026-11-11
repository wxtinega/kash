package com.kash.app.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.kash.app.KashApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Wakes when a new SMS arrives and refreshes Kash in the background, so a new M-Pesa payment
 * shows up without opening the app. Android only delivers this to apps holding RECEIVE_SMS,
 * and only from the system (BROADCAST_SMS permission in the manifest).
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val app = context.applicationContext as KashApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                delay(2_000) // give the system a moment to write the message to the inbox
                app.repository.sync()
                // Post before finishing, in case Android ends the process right after.
                TransactionNotifier.onState(app, app.repository.state.value, app.prefs, app.onScreen)
            } catch (_: Throwable) {
                // Next sync (app open or background worker) will pick it up.
            } finally {
                pending.finish()
            }
        }
    }
}
