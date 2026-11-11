package com.gandalondon.rinse

import android.content.Context
import com.gandalondon.rinse.hc.Dishwasher
import com.gandalondon.rinse.system.CheckWorker
import com.gandalondon.rinse.system.Notify
import com.gandalondon.rinse.system.RinseTile
import com.gandalondon.rinse.system.RinseWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One shared store and dishwasher, plus the step that pushes every change to the widget, tile and notifications. */
object Rinse {
    @Volatile private var store: Store? = null
    @Volatile private var dw: Dishwasher? = null

    fun store(c: Context): Store = store ?: synchronized(this) { store ?: Store(c).also { store = it } }
    fun dishwasher(c: Context): Dishwasher = dw ?: synchronized(this) { dw ?: Dishwasher(store(c)).also { dw = it } }

    suspend fun refresh(c: Context): Snapshot = withContext(Dispatchers.IO) { dishwasher(c).refresh() }.also { publish(c, it) }

    /** [fromOutside] is true for the widget and tile: problems then also arrive as a notification. */
    suspend fun start(c: Context, fromOutside: Boolean = false): Snapshot {
        store(c).snapshot = store(c).snapshot.copy(phase = Phase.Starting, programKey = store(c).program)
        publish(c, store(c).snapshot)
        val snap = withContext(Dispatchers.IO) { dishwasher(c).start() }
        publish(c, snap)
        if (fromOutside && snap.phase == Phase.Blocked) Notify.problem(c, snap)
        return snap
    }

    suspend fun stop(c: Context): Snapshot = withContext(Dispatchers.IO) { dishwasher(c).stop() }.also { publish(c, it) }

    fun publish(c: Context, snap: Snapshot) {
        RinseWidget.updateAll(c, snap)
        RinseTile.requestUpdate(c)
        when (snap.phase) {
            Phase.Running -> CheckWorker.scheduleFor(c, snap.endAt)
            Phase.Done -> {
                val s = store(c)
                if (s.notifiedEnd != snap.endAt) {
                    s.notifiedEnd = snap.endAt
                    Notify.clean(c, snap)
                }
            }
            Phase.Idle -> Notify.clear(c)
            else -> {}
        }
    }

    fun time(ms: Long): String = SimpleDateFormat("HH:mm", Locale.UK).format(Date(ms))

    fun duration(minutes: Long): String = "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')}"

    fun left(snap: Snapshot): String =
        duration(((snap.endAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)) + " left"

    /** "Done at 05:10" for an idle machine, if we know how long the programme takes. */
    fun doneAt(c: Context, key: String): String? =
        store(c).minutes(key)?.let { "Done at " + time(System.currentTimeMillis() + it * 60_000L) }
}
