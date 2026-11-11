package com.kash.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kash.app.KashApp
import java.time.Duration
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * Sends "Your October summary is ready" at 8:00 AM on the 1st of every month.
 *
 * Months end on different dates (28, 29, 30 or 31 days), so instead of counting days the next run is
 * computed from the calendar: the first day of the next month, 08:00 local time. After each run it
 * schedules the following month. The 3-hourly sync also checks on the 1st–3rd as a fallback (phone
 * off at 8 AM, battery saver), and the notification is sent at most once per month either way.
 */
class MonthlySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = (applicationContext as KashApp).repository
            repo.sync() // include anything that arrived overnight on the last day
            ReportNotifier.maybeNotify(applicationContext, repo.state.value)
            Result.success()
        } catch (t: Throwable) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } finally {
            schedule(applicationContext, replace = true)
        }
    }

    companion object {
        private const val NAME = "kash-monthly-summary"
        const val HOUR = 8

        /** Next 1st of the month at 08:00, from [now]. On the 1st before 8 AM, that's today. */
        fun nextRun(now: LocalDateTime): LocalDateTime {
            val thisMonth = YearMonth.from(now).atDay(1).atTime(HOUR, 0)
            return if (now.isBefore(thisMonth)) thisMonth else YearMonth.from(now).plusMonths(1).atDay(1).atTime(HOUR, 0)
        }

        fun schedule(context: Context, replace: Boolean = false) {
            val delay = Duration.between(LocalDateTime.now(), nextRun(LocalDateTime.now())).toMillis().coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<MonthlySummaryWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
