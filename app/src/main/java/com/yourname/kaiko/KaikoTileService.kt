package com.yourname.kaiko

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Quick Settings Tile Service for Kaiko.
 * Provides the closest officially supported Android equivalent to a lock-screen emergency trigger:
 * Users can pull down the Quick Settings shade directly from the secure lock screen and tap this tile.
 */
@RequiresApi(Build.VERSION_CODES.N)
class KaikoTileService : TileService() {

    companion object {
        private const val TAG = "KAIKO_DEBUG"
    }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "Quick Settings Emergency Tile tapped.")
        val tile = qsTile
        tile?.state = Tile.STATE_ACTIVE
        tile?.updateTile()

        // Trigger the emergency SOS alert
        TriggerManager.fireAlert(applicationContext, "quick_settings_tile")

        // Reset tile state after trigger
        tile?.state = Tile.STATE_INACTIVE
        tile?.updateTile()
    }
}
