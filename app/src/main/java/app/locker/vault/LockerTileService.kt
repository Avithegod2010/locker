package app.locker.vault

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.locker.vault.core.SessionState

/**
 * One-tap lock from the Quick Settings shade. It never exposes vault contents and
 * does not attempt to unlock anything.
 */
class LockerTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val container = (application as LockerApp).container
        container.lockNow()
        updateTile()
        android.widget.Toast.makeText(
            this,
            "Locker is locked",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val unlocked = (application as LockerApp).container.session.state.value == SessionState.UNLOCKED
        tile.state = if (unlocked) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Lock Locker"
        tile.updateTile()
    }
}
