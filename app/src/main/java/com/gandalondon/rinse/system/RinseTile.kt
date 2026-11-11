package com.gandalondon.rinse.system

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.gandalondon.rinse.MainActivity
import com.gandalondon.rinse.Phase
import com.gandalondon.rinse.Programs
import com.gandalondon.rinse.Rinse

/** Quick settings tile: pull down, tap, tonight's wash starts. Lit while it runs. */
class RinseTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        draw()
    }

    override fun onClick() {
        super.onClick()
        val snap = Rinse.store(this).snapshot
        if (snap.phase == Phase.Idle) {
            sendBroadcast(Intent(this, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_START))
        } else {
            openApp()
        }
    }

    private fun draw() {
        val tile = qsTile ?: return
        val snap = Rinse.store(this).snapshot
        tile.label = "Rinse"
        tile.subtitle = when (snap.phase) {
            Phase.Unlinked -> "Open to link"
            Phase.Idle -> "Start ${Programs.name(Rinse.store(this).program)}"
            Phase.Starting -> "Starting"
            Phase.Running -> "Done at ${Rinse.time(snap.endAt)}"
            Phase.Done -> Rinse.statement(this, snap).removeSuffix(".")
            Phase.Blocked -> snap.head.substringBefore(". ").removeSuffix(".")
        }
        tile.state = when (snap.phase) {
            Phase.Running, Phase.Starting -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        fun requestUpdate(c: Context) {
            try {
                requestListeningState(c, ComponentName(c, RinseTile::class.java))
            } catch (_: Exception) { }
        }
    }
}
