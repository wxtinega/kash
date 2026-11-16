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
import com.kash.app.ui.report.reportEntries
import com.kash.insights.Budgets
import com.kash.insights.MonthlyReports
import java.time.LocalDate
import java.time.YearMonth

/**
 * One heads-up when a budget passes 75%, and one if it goes over. Each at most once per month.
 * Only when budgets are switched on. The lock screen shows no amounts.
 */
object BudgetNotifier {
    private const val CHANNEL = "budgets"

    fun check(context: Context, state: RepoState, prefs: KashPrefs, onScreen: Boolean, today: LocalDate) {
        if (!state.ready || !prefs.budgetsEnabled()) return
        val budgets = prefs.budgets()
        if (budgets.isEmpty()) return
        val month = YearMonth.from(today)
        val report = MonthlyReports.build(state.reportEntries(), month)
        val due = Budgets.alerts(Budgets.status(budgets, report, today), month, prefs.budgetAlertsSent())
        if (due.isEmpty()) return
        prefs.markBudgetAlerts(due.map { it.key })
        if (onScreen || !canNotify(context)) return // on screen: the Budgets card already shows it

        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        val open = PendingIntent.getActivity(
            context, 7_001,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        due.forEach { a ->
            val public = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_kash)
                .setContentTitle("Budget update")
                .setContentText("Unlock to see it.")
                .build()
            nm.notify(
                20_000 + (a.key.hashCode() and 0xffff),
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_kash)
                    .setContentTitle(a.title)
                    .setContentText(a.body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(a.body))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(public)
                    .build(),
            )
        }
    }

    private fun canNotify(context: Context): Boolean =
        (android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Budgets", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When a budget passes 75% or goes over"
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
            )
        }
    }
}
