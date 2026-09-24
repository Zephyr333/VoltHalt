package com.im_atp.volthalt

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import android.util.Log
import kotlinx.coroutines.launch

/**
 * Quick Settings tile for VoltHalt.
 *
 * The tile represents the max-battery alarm toggle, but shows as "active"
 * whenever either alarm is enabled — so it accurately reflects whether any
 * background monitoring is happening.
 *
 * Tapping toggles the persisted max alarm. Low-only monitoring stays active.
 */
class AlarmTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var preferencesManager: PreferencesManager
    private var listeningJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(applicationContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        listeningJob?.cancel()
        listeningJob = serviceScope.launch {
            preferencesManager.monitoringSettingsFlow.collect { applyTileState(it.enabled) }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        listeningJob?.cancel()
        listeningJob = null
    }

    override fun onClick() {
        super.onClick()

        serviceScope.launch {
            try {
                val current = preferencesManager.monitoringSettingsFlow.first()
                preferencesManager.setAlarmEnabled(!current.maxEnabled)
                MonitoringController.reconcile(applicationContext)
                applyTileState(preferencesManager.monitoringSettingsFlow.first().enabled)
            } catch (e: Exception) {
                Log.e("VoltHaltMonitoring", "Unable to toggle max alarm from tile", e)
            }
        }
    }

    private fun applyTileState(active: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (active) "Alarm On" else "Alarm Off"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (active) "Monitoring" else "Disabled"
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
