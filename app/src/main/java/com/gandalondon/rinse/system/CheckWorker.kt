package com.gandalondon.rinse.system

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gandalondon.rinse.Rinse
import java.util.concurrent.TimeUnit

/**
 * Checks on a running wash in the background: every half hour, and just after
 * it should finish. Rinse.refresh reschedules it while the wash is still running,
 * and posts the Clean notification once it is done.
 */
class CheckWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        Rinse.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "check"

        fun scheduleFor(c: Context, endAt: Long) {
            val untilEnd = endAt + 2 * 60_000L - System.currentTimeMillis()
            val delay = untilEnd.coerceIn(60_000L, 30 * 60_000L)
            val req = OneTimeWorkRequestBuilder<CheckWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(c).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
