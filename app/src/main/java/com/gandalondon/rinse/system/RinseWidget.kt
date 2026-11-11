package com.gandalondon.rinse.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
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

    override fun onAppWidgetOptionsChanged(c: Context, mgr: AppWidgetManager, id: Int, options: Bundle) {
        render(c, mgr, intArrayOf(id), Rinse.store(c).snapshot)
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
            for (id in ids) {
                // Portrait size in dp. One row: plate left, statement beside it. Taller: plate top left, statement at the bottom.
                val o = mgr.getAppWidgetOptions(id)
                val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 164).coerceAtLeast(60)
                val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 164).coerceAtLeast(40)
                val oneRow = h < 100
                val views = RemoteViews(c.packageName, if (oneRow) R.layout.widget_row else R.layout.widget).apply {
                    setInt(R.id.root, "setBackgroundResource", bg)
                    setInt(R.id.icon, "setColorFilter", fg)
                    // Two sizes only: big for a one-word statement (Clean.), one smaller size for everything else.
                    val oneWord = !big.trim().contains(' ')
                    setImageViewBitmap(R.id.big,
                        if (oneRow) text(c, big, fg, w - 32 - 28 - 12, h, maxSp = if (oneWord) 18f else 14f, minSp = 14f, lines = 2, bottom = false)
                        else text(c, big, fg, w - 32, h - 32 - 32 - 8, maxSp = if (oneWord) 28f else 20f, minSp = 20f, lines = 4, bottom = true))
                    setContentDescription(R.id.big, big)
                    setOnClickPendingIntent(R.id.root, tapIntent(c, snap))
                }
                mgr.updateAppWidget(id, views)
            }
        }

        /** The statement in Geist Medium at one of two sizes (it only drops to the smaller size if the big one will not fit). */
        private fun text(c: Context, s: String, color: Int, wDp: Int, hDp: Int, maxSp: Float, minSp: Float, lines: Int, bottom: Boolean): Bitmap {
            val d = c.resources.displayMetrics
            val w = (wDp.coerceAtLeast(20) * d.density).toInt()
            val h = (hDp.coerceAtLeast(16) * d.density).toInt()
            val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                typeface = ResourcesCompat.getFont(c, R.font.geist_medium)
            }
            var sp = maxSp
            var layout: StaticLayout
            while (true) {
                paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, d)
                layout = StaticLayout.Builder.obtain(s, 0, s.length, paint, w)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
                    .build()
                if ((layout.lineCount <= lines && layout.height <= h) || sp <= minSp) break
                sp -= 1f
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.translate(0f, (if (bottom) h - layout.height else (h - layout.height) / 2).coerceAtLeast(0).toFloat())
            layout.draw(canvas)
            return bmp
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
