package com.kash.app.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kash.app.MainActivity
import com.kash.app.R
import com.kash.app.data.KashPrefs
import com.kash.app.data.RepoState
import com.kash.insights.Category
import java.time.LocalDateTime

/**
 * "KSh 200 sent to JANE. What was it for?" right after a payment Kash can't sort by itself.
 *
 * Rules:
 *  - Only payments that arrive after Kash was installed/updated to this version. History is never notified.
 *  - Each payment is asked about at most once. Ignoring it leaves it unsorted; it waits in the app
 *    until the user sorts it there. No reminders.
 *  - When the user answers in the app, the notification disappears.
 *  - The lock screen only ever shows "New payment to sort" (no amounts, no names).
 *  - The quick-answer buttons need the phone to be unlocked first (Android 12+).
 */
object TransactionNotifier {
    const val CHANNEL = "new_payments"
    private const val MAX_AT_ONCE = 3 // a burst of messages never becomes a wall of notifications

    @Synchronized // the SMS receiver, background sync and the app can all call this at once
    fun onState(context: Context, state: RepoState, prefs: KashPrefs, onScreen: Boolean, now: LocalDateTime = LocalDateTime.now()) {
        val after = prefs.notifyAfter() ?: run {
            prefs.setNotifyAfter(now) // first run of this version: everything already in the inbox is history
            return
        }
        val nm = NotificationManagerCompat.from(context)
        val notified = prefs.notified()
        val open = state.questions.map { it.txId }.toSet()

        // Sorted in the app (or sorted itself after a new answer covered it): remove the notification.
        notified.filter { it !in open }.forEach { nm.cancel(id(it)) }

        val fresh = state.questions
            .filter { it.txId !in notified && it.at != null && it.at.isAfter(after) && it.at.isAfter(now.minusDays(2)) }
            .sortedByDescending { it.at }
        if (fresh.isEmpty()) return
        prefs.markNotified(fresh.map { it.txId })
        if (onScreen || !canNotify(context)) return // on screen: the in-app card already asks

        ensureChannel(context)
        for (q in fresh.take(MAX_AT_ONCE)) {
            val verb = if (q.incoming) "from" else "to"
            val text = "${q.amount.format()} ${if (q.incoming) "received" else "sent"} $verb ${q.title}"
            val public = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_kash)
                .setContentTitle("New payment to sort")
                .setContentText("Unlock to see it.")
                .build()
            val b = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_kash)
                .setContentTitle(text)
                .setContentText(if (q.incoming) "What was this money for?" else "What was it for?")
                .setContentIntent(openApp(context, q.txId))
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(public)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setWhen(q.at!!.atZone(java.time.ZoneId.of("Africa/Nairobi")).toInstant().toEpochMilli())
            q.choices.filter { it != Category.OTHER }.take(2).forEachIndexed { i, c ->
                b.addAction(
                    NotificationCompat.Action.Builder(0, c.label, answer(context, q.txId, c, i))
                        .setAuthenticationRequired(true)
                        .build(),
                )
            }
            b.addAction(0, "Other…", openApp(context, q.txId))
            nm.notify(id(q.txId), b.build())
        }
    }

    fun id(txId: String): Int = 10_000 + (txId.hashCode() and 0x3fff_ffff) % 1_000_000

    private fun answer(context: Context, txId: String, category: Category, slot: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id(txId) * 4 + slot,
            Intent(context, NotificationAnswerReceiver::class.java)
                .putExtra(NotificationAnswerReceiver.EXTRA_TX, txId)
                .putExtra(NotificationAnswerReceiver.EXTRA_CATEGORY, category.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openApp(context: Context, txId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            id(txId) * 4 + 3,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun canNotify(context: Context): Boolean =
        (android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "New payments", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Asks what a new payment was for, right after it happens"
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
            )
        }
    }
}
