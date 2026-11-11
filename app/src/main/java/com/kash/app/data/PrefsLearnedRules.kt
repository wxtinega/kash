package com.kash.app.data

import android.content.Context
import com.kash.app.security.SecureStore
import com.kash.insights.Category
import com.kash.insights.LearnedRules
import com.kash.insights.MerchantKey

/**
 * The user's "What was this?" answers. Keys can contain names and phone numbers, so both
 * keys and values live in encrypted storage. Answers saved by older versions are moved in once.
 */
class PrefsLearnedRules(context: Context) : LearnedRules {
    private val store = SecureStore(context, "learned")

    init {
        SecureStore.migrate(context, "kash_learned_rules") { old ->
            old.forEach { (k, v) -> if (v is String) store.putString(k, v) }
        }
    }

    override fun get(key: MerchantKey): Category? =
        store.getString(key.value)?.let { runCatching { Category.valueOf(it) }.getOrNull() }

    override fun put(key: MerchantKey, category: Category) = store.putString(key.value, category.name)
}
