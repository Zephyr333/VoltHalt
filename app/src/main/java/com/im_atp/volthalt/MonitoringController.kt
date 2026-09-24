package com.im_atp.volthalt

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first

object MonitoringController {
    /** Call after the toggle has been persisted, from a user action or boot broadcast. */
    suspend fun reconcile(context: Context) {
        val app = context.applicationContext
        val settings = PreferencesManager(app).monitoringSettingsFlow.first()
        val intent = Intent(app, BatteryService::class.java)
        if (settings.enabled) {
            try {
                ContextCompat.startForegroundService(app, intent)
                Log.i("VoltHaltMonitoring", "Monitoring requested: max=${settings.maxEnabled}, low=${settings.lowEnabled}")
            } catch (e: IllegalStateException) {
                Log.e("VoltHaltMonitoring", "System rejected foreground service start; preferences retained", e)
            } catch (e: SecurityException) {
                Log.e("VoltHaltMonitoring", "Foreground service permission denied; preferences retained", e)
            }
        } else {
            app.stopService(intent)
        }
    }
}
