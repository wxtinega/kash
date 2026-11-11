package com.kash.app.data

import android.content.Context
import com.kash.insights.Category
import com.kash.insights.LearnedRules
import com.kash.insights.MerchantKey

/** The user's "What was this?" answers, kept in app-private storage so they survive restarts. */
class PrefsLearnedRules(context: Context) : LearnedRules {
    private val prefs = context.getSharedPreferences("kash_learned_rules", Context.MODE_PRIVATE)

    override fun get(key: MerchantKey): Category? =
        prefs.getString(key.value, null)?.let { runCatching { Category.valueOf(it) }.getOrNull() }

    override fun put(key: MerchantKey, category: Category) {
        prefs.edit().putString(key.value, category.name).apply()
    }
}
