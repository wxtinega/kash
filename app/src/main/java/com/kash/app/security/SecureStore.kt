package com.kash.app.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted key–value storage for anything personal Kash keeps.
 *
 *  - Values: AES-256-GCM. The key is generated inside the Android Keystore (hardware-backed where
 *    the phone supports it) and can never be read out of it, not even by Kash.
 *  - Names: stored as SHA-256 hashes, so even the labels (which can contain names or phone numbers,
 *    e.g. "person:0722…") aren't readable from the file.
 *  - Tampered or unreadable entries are treated as missing, never crash the app.
 *
 * Uses only the platform's own crypto (no third-party library).
 */
class SecureStore(context: Context, name: String) {

    private val prefs: SharedPreferences = context.getSharedPreferences("kash_secure_$name", Context.MODE_PRIVATE)

    /** Decrypted values are kept in memory only while Kash runs, so reads stay instant. */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Slot>()
    private class Slot(val value: String?)

    fun getString(key: String): String? = cache.getOrPut(key) {
        val stored = prefs.getString(hash(key), null)
        Slot(stored?.let { runCatching { String(decrypt(Base64.decode(it, Base64.NO_WRAP)), Charsets.UTF_8) }.getOrNull() })
    }.value

    fun putString(key: String, value: String) {
        cache[key] = Slot(value)
        prefs.edit().putString(hash(key), Base64.encodeToString(encrypt(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)).apply()
    }

    fun remove(key: String) {
        cache[key] = Slot(null)
        prefs.edit().remove(hash(key)).apply()
    }

    fun getLong(key: String): Long? = getString(key)?.toLongOrNull()
    fun putLong(key: String, value: Long) = putString(key, value.toString())

    fun getStringSet(key: String): Set<String> =
        getString(key)?.split('\n')?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    fun putStringSet(key: String, values: Set<String>) = putString(key, values.joinToString("\n"))

    private fun hash(key: String): String =
        MessageDigest.getInstance("SHA-256").digest("kash:$key".toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private const val ALIAS = "kash_master_key_v1"
        private const val GCM_TAG_BITS = 128

        @Synchronized
        private fun key(): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            gen.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            return gen.generateKey()
        }

        /** Output: 12-byte IV followed by ciphertext+tag. A fresh random IV every time. */
        fun encrypt(plain: ByteArray): ByteArray {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            return c.iv + c.doFinal(plain)
        }

        fun decrypt(data: ByteArray): ByteArray {
            require(data.size > 12) { "Too short" }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, data, 0, 12))
            return c.doFinal(data, 12, data.size - 12)
        }

        /**
         * One-time move of an old plain-text preferences file into encrypted storage, then delete it.
         * [convert] maps each old entry to the new store; afterwards the old file no longer exists.
         */
        fun migrate(context: Context, oldName: String, convert: (Map<String, *>) -> Unit) {
            val old = context.getSharedPreferences(oldName, Context.MODE_PRIVATE)
            val all = old.all
            if (all.isEmpty()) return
            convert(all)
            old.edit().clear().commit()
            context.deleteSharedPreferences(oldName)
        }
    }
}
