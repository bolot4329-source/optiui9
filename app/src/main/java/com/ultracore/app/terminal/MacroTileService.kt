package com.ultracore.app.terminal

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.ultracore.app.MainActivity

/** Quick-settings tile «Макрос»: runs the macro marked with a star. */
class MacroTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val macro = MacroStore(this).favorite()
        if (macro == null) {
            openApp()
            return
        }
        MacroRunner.runInBackground(applicationContext, macro)
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val macro = MacroStore(this).favorite()
        tile.label = macro?.name ?: "Макрос"
        tile.subtitle = if (macro == null) "не выбран" else "UltraCore"
        tile.state = if (macro == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
