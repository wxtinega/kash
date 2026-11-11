package com.kash.app.data

import android.content.Context
import com.kash.app.security.SecureStore
import com.kash.parser.Money
import com.kash.parser.Provider
import java.time.LocalDateTime

/**
 * Small choices that change how messages are read, all kept encrypted:
 *  - sender IDs the user confirmed as their bank (or said are not),
 *  - accounts the user removed,
 *  - balances the user typed in for accounts whose messages never state one.
 * Values saved by older (unencrypted) versions are moved in once and the old file deleted.
 */
class KashPrefs(context: Context) {
    private val store = SecureStore(context, "prefs")

    init {
        SecureStore.migrate(context, "kash_prefs") { old ->
            old.forEach { (k, v) ->
                @Suppress("UNCHECKED_CAST")
                when (v) {
                    is String -> store.putString(k, v)
                    is Long -> store.putLong(k, v)
                    is Set<*> -> store.putStringSet(k, (v as Set<String>))
                }
            }
        }
    }

    fun confirmedSenders(): Map<String, Provider> =
        store.getStringSet("confirmed_senders").mapNotNull { e ->
            val (sender, provider) = e.split('|').takeIf { it.size == 2 } ?: return@mapNotNull null
            runCatching { sender to Provider.valueOf(provider) }.getOrNull()
        }.toMap()

    fun rejectedSenders(): Set<String> = store.getStringSet("rejected_senders")

    fun confirmSender(sender: String, provider: Provider) =
        store.putStringSet("confirmed_senders", store.getStringSet("confirmed_senders") + "$sender|${provider.name}")

    fun rejectSender(sender: String) = store.putStringSet("rejected_senders", rejectedSenders() + sender)

    /** Accounts the user removed: their messages are ignored completely. */
    fun ignoredProviders(): Set<Provider> =
        store.getStringSet("ignored_providers").mapNotNull { runCatching { Provider.valueOf(it) }.getOrNull() }.toSet()

    fun setTracked(provider: Provider, tracked: Boolean) {
        val now = store.getStringSet("ignored_providers")
        store.putStringSet("ignored_providers", if (tracked) now - provider.name else now + provider.name)
    }

    /** Balance the user entered, and when. Messages after that time move it up or down. */
    fun manualBalances(): Map<Provider, Pair<Money, LocalDateTime>> =
        Provider.entries.mapNotNull { p ->
            val cents = store.getLong("bal_${p.name}") ?: return@mapNotNull null
            val at = store.getString("bal_at_${p.name}") ?: return@mapNotNull null
            runCatching { p to (Money(cents) to LocalDateTime.parse(at)) }.getOrNull()
        }.toMap()

    fun setManualBalance(provider: Provider, balance: Money, at: LocalDateTime = LocalDateTime.now()) {
        store.putLong("bal_${provider.name}", balance.cents)
        store.putString("bal_at_${provider.name}", at.toString())
    }

    /** Only transactions that arrive after this moment get a notification (no history spam). */
    fun notifyAfter(): LocalDateTime? = store.getString("notify_after")?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
    fun setNotifyAfter(at: LocalDateTime) = store.putString("notify_after", at.toString())

    /** Transactions already asked about once by notification; never asked again that way. */
    fun notified(): Set<String> = store.getStringSet("notified_tx")
    fun markNotified(ids: Collection<String>) {
        // Keep the list small: only the most recent few hundred matter.
        store.putStringSet("notified_tx", (notified() + ids).toList().takeLast(400).toSet())
    }
}
