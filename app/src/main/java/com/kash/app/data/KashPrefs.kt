package com.kash.app.data

import android.content.Context
import com.kash.parser.Money
import com.kash.parser.Provider
import java.time.LocalDateTime

/**
 * Small app-private choices that change how messages are read:
 *  - sender IDs the user confirmed as their bank (or said are not),
 *  - balances the user typed in for accounts whose messages never state one.
 */
class KashPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("kash_prefs", Context.MODE_PRIVATE)

    fun confirmedSenders(): Map<String, Provider> =
        prefs.getStringSet("confirmed_senders", emptySet()).orEmpty().mapNotNull { e ->
            val (sender, provider) = e.split('|').takeIf { it.size == 2 } ?: return@mapNotNull null
            runCatching { sender to Provider.valueOf(provider) }.getOrNull()
        }.toMap()

    fun rejectedSenders(): Set<String> = prefs.getStringSet("rejected_senders", emptySet()).orEmpty()

    fun confirmSender(sender: String, provider: Provider) {
        val set = prefs.getStringSet("confirmed_senders", emptySet()).orEmpty() + "$sender|${provider.name}"
        prefs.edit().putStringSet("confirmed_senders", set).apply()
    }

    fun rejectSender(sender: String) {
        prefs.edit().putStringSet("rejected_senders", rejectedSenders() + sender).apply()
    }

    /** Balance the user entered, and when. Messages after that time move it up or down. */
    fun manualBalances(): Map<Provider, Pair<Money, LocalDateTime>> =
        Provider.entries.mapNotNull { p ->
            val cents = prefs.getLong("bal_${p.name}", Long.MIN_VALUE)
            val at = prefs.getString("bal_at_${p.name}", null)
            if (cents == Long.MIN_VALUE || at == null) null
            else runCatching { p to (Money(cents) to LocalDateTime.parse(at)) }.getOrNull()
        }.toMap()

    fun setManualBalance(provider: Provider, balance: Money, at: LocalDateTime = LocalDateTime.now()) {
        prefs.edit().putLong("bal_${provider.name}", balance.cents).putString("bal_at_${provider.name}", at.toString()).apply()
    }
}
