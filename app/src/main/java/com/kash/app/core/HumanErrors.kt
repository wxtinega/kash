package com.kash.app.core

import android.database.sqlite.SQLiteFullException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Turns any failure into a sentence a person can act on. Raw exception text never reaches the UI.
 * (Details go to the local security/diagnostic log without amounts or message contents.)
 */
object HumanErrors {
    fun message(t: Throwable): String = when (t) {
        is UnknownHostException, is SocketTimeoutException, is IOException ->
            "You're offline. Kash still works; anything that needs the internet will finish when you're back online."
        is SecurityException ->
            "Kash doesn't have permission to read your messages. You can allow it in Settings → Permissions."
        is SQLiteFullException ->
            "Your phone is almost out of storage, so Kash couldn't save that. Free up some space and try again."
        else ->
            "That didn't go through. Your data is safe; please try again."
    }
}
