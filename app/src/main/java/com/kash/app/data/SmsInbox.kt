package com.kash.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.kash.parser.RawSms
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Reads the phone's SMS inbox. Only called after the user has granted permission, and only
 * the sender, text and time are read. Nothing leaves the phone.
 */
object SmsInbox {

    /** How far back the first import looks. Enough for meaningful history without a slow start. */
    private const val HISTORY_DAYS = 180L

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun read(context: Context): List<RawSms> {
        if (!hasPermission(context)) return emptyList()
        val since = Instant.now().minus(HISTORY_DAYS, ChronoUnit.DAYS).toEpochMilli()
        val out = ArrayList<RawSms>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC", // oldest first: later messages build on earlier ones (e.g. learned accounts)
        )?.use { c ->
            val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val sender = c.getString(iAddr) ?: continue
                val body = c.getString(iBody) ?: continue
                out += RawSms(sender, body, Instant.ofEpochMilli(c.getLong(iDate)))
            }
        }
        return out
    }
}
