package com.kash.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kash.app.KashApp
import java.util.concurrent.TimeUnit

/**
 * Background synchronisation: re-reads new messages and recomputes balances, categories and
 * check-ins while the app is closed, so opening Kash never shows a loading screen.
 * Needs no internet. Battery-friendly: runs at most every few hours and only when the battery
 * isn't low.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val repo = (applicationContext as KashApp).repository
        repo.sync()
        ReportNotifier.maybeNotify(applicationContext, repo.state.value)
        Result.success()
    } catch (t: Throwable) {
        // Transient problems (e.g. database busy): let WorkManager retry with back-off.
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    companion object {
        private const val NAME = "kash-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS)
                .setConstraints(
                    androidx.work.Constraints.Builder().setRequiresBatteryNotLow(true).build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
