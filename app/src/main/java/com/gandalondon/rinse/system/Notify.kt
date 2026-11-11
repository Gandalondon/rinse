package com.gandalondon.rinse.system

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gandalondon.rinse.MainActivity
import com.gandalondon.rinse.R
import com.gandalondon.rinse.Rinse
import com.gandalondon.rinse.Snapshot

object Notify {
    private const val DONE = "done"
    private const val PROBLEM = "problem"
    private const val ID_DONE = 1
    private const val ID_PROBLEM = 2

    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(DONE, "Clean", NotificationManager.IMPORTANCE_LOW).apply {
            description = "When a wash finishes. Silent, so it waits for the morning."
        })
        nm.createNotificationChannel(NotificationChannel(PROBLEM, "Could not start", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "When the widget or tile could not start the dishwasher."
        })
    }

    private fun open(c: Context) = PendingIntent.getActivity(
        c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission")
    private fun post(c: Context, id: Int, channel: String, title: String, silent: Boolean) {
        val nm = NotificationManagerCompat.from(c)
        if (!nm.areNotificationsEnabled()) return
        channels(c)
        nm.notify(id, NotificationCompat.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_plate)
            .setContentTitle(title)
            .setContentIntent(open(c))
            .setAutoCancel(true)
            .setSilent(silent)
            .build())
    }

    fun clean(c: Context, snap: Snapshot) =
        post(c, ID_DONE, DONE, Rinse.statement(c, snap), silent = true)

    fun problem(c: Context, snap: Snapshot) =
        post(c, ID_PROBLEM, PROBLEM, snap.head, silent = false)

    fun clear(c: Context, keepDone: Boolean = false) {
        if (!keepDone) NotificationManagerCompat.from(c).cancel(ID_DONE)
        NotificationManagerCompat.from(c).cancel(ID_PROBLEM)
    }
}
