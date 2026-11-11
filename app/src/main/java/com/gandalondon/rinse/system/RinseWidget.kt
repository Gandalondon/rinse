package com.gandalondon.rinse.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews
import com.gandalondon.rinse.MainActivity
import com.gandalondon.rinse.Phase
import com.gandalondon.rinse.R
import com.gandalondon.rinse.Rinse
import com.gandalondon.rinse.Snapshot

/** Home screen widget: the plate and one statement. Black at night, White when clean, Brick when blocked. */
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
            val big = Rinse.statement(c, snap, widget = true)
            val (bg, fg) = when (snap.phase) {
                Phase.Done -> R.drawable.widget_day to c.getColor(R.color.ink)
                Phase.Blocked -> R.drawable.widget_brick to c.getColor(R.color.paper)
                else -> R.drawable.widget_night to c.getColor(R.color.paper)
            }
            fun build(layout: Int) = RemoteViews(c.packageName, layout).apply {
                setInt(R.id.root, "setBackgroundResource", bg)
                setTextViewText(R.id.big, big)
                setTextColor(R.id.big, fg)
                setInt(R.id.icon, "setColorFilter", fg)
                setOnClickPendingIntent(R.id.root, tapIntent(c, snap))
            }
            // One row: plate left, statement beside it. Taller: plate top left, statement at the bottom.
            val views = if (Build.VERSION.SDK_INT >= 31) RemoteViews(mapOf(
                SizeF(100f, 40f) to build(R.layout.widget_row),
                SizeF(100f, 100f) to build(R.layout.widget),
            )) else build(R.layout.widget)
            mgr.updateAppWidget(ids, views)
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
