package com.gandalondon.rinse.system

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gandalondon.rinse.Phase
import com.gandalondon.rinse.Rinse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Starts the wash from the widget, without opening the app. */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(c: Context, intent: Intent) {
        if (intent.action != ACTION_START) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                // Check the real state first: only start if Ran is genuinely ready.
                val now = Rinse.refresh(c.applicationContext)
                if (now.phase == Phase.Idle) Rinse.start(c.applicationContext, fromOutside = true)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_START = "com.gandalondon.rinse.START"

        fun startIntent(c: Context): PendingIntent = PendingIntent.getBroadcast(
            c, 1, Intent(c, ActionReceiver::class.java).setAction(ACTION_START),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
