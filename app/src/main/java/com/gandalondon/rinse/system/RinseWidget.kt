package com.gandalondon.rinse.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.gandalondon.rinse.MainActivity
import com.gandalondon.rinse.Phase
import com.gandalondon.rinse.Programs
import com.gandalondon.rinse.R
import com.gandalondon.rinse.Rinse
import com.gandalondon.rinse.Snapshot

/** Home screen widget: one big word and one line. Black at night, White when clean, Brick when blocked. */
class RinseWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(c, mgr, ids, Rinse.store(c).snapshot)
    }

    companion object {
        fun updateAll(c: Context, snap: Snapshot) {
            val mgr = AppWidgetManager.getInstance(c)
            val ids = mgr.getAppWidgetIds(ComponentName(c, RinseWidget::class.java))
            if (ids.isNotEmpty()) render(c, mgr, ids, snap)
        }

        private fun render(c: Context, mgr: AppWidgetManager, ids: IntArray, snap: Snapshot) {
            val (big, line) = words(c, snap)
            val (bg, fg, muted) = when (snap.phase) {
                Phase.Done -> Triple(R.drawable.widget_day, c.getColor(R.color.ink), c.getColor(R.color.muted_day))
                Phase.Blocked -> Triple(R.drawable.widget_brick, c.getColor(R.color.paper), c.getColor(R.color.muted_brick))
                else -> Triple(R.drawable.widget_night, c.getColor(R.color.paper), c.getColor(R.color.muted_night))
            }
            val views = RemoteViews(c.packageName, R.layout.widget).apply {
                setInt(R.id.root, "setBackgroundResource", bg)
                setTextViewText(R.id.big, big)
                setTextColor(R.id.big, fg)
                setTextViewText(R.id.line, line)
                setTextColor(R.id.line, muted)
                setOnClickPendingIntent(R.id.root, tapIntent(c, snap))
            }
            mgr.updateAppWidget(ids, views)
        }

        private fun words(c: Context, snap: Snapshot): Pair<String, String> = when (snap.phase) {
            Phase.Unlinked -> "Rinse" to "Open to link your dishwasher"
            Phase.Idle -> Programs.name(Rinse.store(c).program) to "Tap to start"
            Phase.Starting -> "Starting" to Programs.name(snap.programKey)
            Phase.Running -> Rinse.time(snap.endAt) to Rinse.left(snap)
            Phase.Done -> "Clean" to "Unload me"
            Phase.Blocked -> snap.head to snap.body
        }

        /** Idle: tap starts the wash. Anything else: tap opens the app. */
        private fun tapIntent(c: Context, snap: Snapshot): PendingIntent =
            if (snap.phase == Phase.Idle) ActionReceiver.startIntent(c)
            else PendingIntent.getActivity(
                c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
