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
import com.kash.app.data.RepoState
import com.kash.app.ui.report.reportEntries
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Your October summary is ready" on the 1st (or the first background sync in the first 3 days).
 * Two choices: View (opens the report in the app, after unlocking) or Download PDF.
 * The notification never shows amounts, so nothing private appears on the lock screen.
 */
object ReportNotifier {
    const val CHANNEL = "monthly_summary"
    const val EXTRA_OPEN = "kash.open"
    const val EXTRA_MONTH = "kash.month"
    const val OPEN_REPORT = "report"
    const val OPEN_PDF = "pdf"
    private const val PREFS = "kash_notifications"
    private const val KEY_LAST = "last_report_notified"

    fun maybeNotify(context: Context, state: RepoState, now: java.time.LocalDateTime = java.time.LocalDateTime.now()) {
        val today = now.toLocalDate()
        // Window: 1st–3rd of the month, not before 8 AM (never at night).
        if (today.dayOfMonth > 3 || now.hour < MonthlySummaryWorker.HOUR) return
        val month = YearMonth.from(today).minusMonths(1)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST, null) == month.toString()) return
        if (state.reportEntries().none { YearMonth.from(it.date) == month }) return // nothing to report
        if (!canNotify(context)) return

        ensureChannel(context)
        val name = month.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH))
        val view = pending(context, 1, OPEN_REPORT, month)
        val pdf = pending(context, 2, OPEN_PDF, month)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_kash)
            .setContentTitle("Your $name summary is ready")
            .setContentText("See where your money went last month.")
            .setContentIntent(view)
            .addAction(0, "View", view)
            .addAction(0, "Download PDF", pdf)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(100 + month.monthValue, n)
        prefs.edit().putString(KEY_LAST, month.toString()).apply()
    }

    private fun canNotify(context: Context): Boolean =
        (android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Monthly summary", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Tells you when last month's money summary is ready"
                },
            )
        }
    }

    private fun pending(context: Context, code: Int, open: String, month: YearMonth): PendingIntent =
        PendingIntent.getActivity(
            context,
            code,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN, open)
                .putExtra(EXTRA_MONTH, month.toString())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
