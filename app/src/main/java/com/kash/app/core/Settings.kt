package com.kash.app.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kash.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("kash_settings")

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hideAmounts: Boolean = false,
    /** Version of the privacy policy the user agreed to (0 = never). */
    val acceptedPolicyVersion: Int = 0,
)

/** Small, non-sensitive preferences. Financial data lives in the encrypted database. */
class SettingsStore(private val context: Context) {
    private val THEME = stringPreferencesKey("theme_mode")
    private val HIDE = booleanPreferencesKey("hide_amounts")
    private val POLICY = intPreferencesKey("accepted_policy_version")
    private val POLICY_AT = longPreferencesKey("accepted_policy_at")

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            themeMode = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            hideAmounts = p[HIDE] ?: false,
            acceptedPolicyVersion = p[POLICY] ?: 0,
        )
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[THEME] = mode.name }
    suspend fun setHideAmounts(hide: Boolean) = context.dataStore.edit { it[HIDE] = hide }

    /** Records which policy version was accepted, and when (kept as a record of consent). */
    suspend fun acceptPolicy(version: Int) = context.dataStore.edit {
        it[POLICY] = version
        it[POLICY_AT] = System.currentTimeMillis()
    }
}
