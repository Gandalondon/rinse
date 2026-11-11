package com.gandalondon.rinse.system

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gandalondon.rinse.Phase
import com.gandalondon.rinse.Rinse
import com.gandalondon.rinse.hc.Upkeep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Checks on a running wash in the background: every half hour, and just after
 * it should finish. Rinse.refresh reschedules it while the wash is still running,
 * and posts the Clean notification once it is done.
 */
class CheckWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        var snap = Rinse.refresh(c, schedule = false)
        if (snap.phase == Phase.Running && snap.endAt - System.currentTimeMillis() < 12 * 60_000L) {
            // Last minutes of the wash: listen for salt, rinse aid and filter alerts.
            withContext(Dispatchers.IO) {
                try {
                    val store = Rinse.store(c)
                    Upkeep.listen(store, Rinse.dishwasher(c).api.freshToken(), maxMs = 9 * 60_000L)
                } catch (_: Exception) { }
            }
            snap = Rinse.refresh(c, schedule = false)
        }
        // Still running: book the next check (replacing this one, which is ending).
        if (snap.phase == Phase.Running) scheduleFor(c, snap.endAt, replace = true)
        return Result.success()
    }

    companion object {
        private const val NAME = "check"

        /** The app and widget keep an existing check; only the check itself replaces it. */
        fun scheduleFor(c: Context, endAt: Long, replace: Boolean = false) {
            // Wake 6 minutes before the end to listen through the finish.
            val untilEnd = endAt - 6 * 60_000L - System.currentTimeMillis()
            val delay = untilEnd.coerceIn(60_000L, 30 * 60_000L)
            val req = OneTimeWorkRequestBuilder<CheckWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(c).enqueueUniqueWork(NAME, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
        }
    }
}
