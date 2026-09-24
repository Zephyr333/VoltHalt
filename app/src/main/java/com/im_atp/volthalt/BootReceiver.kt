package com.im_atp.volthalt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        Log.i("VoltHaltBoot", "Received ${intent.action}")
        val pendingResult = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Credential-protected DataStore is available after the first unlock.
                withTimeout(8_000) { MonitoringController.reconcile(app) }
            } catch (e: Exception) {
                Log.e("VoltHaltBoot", "Unable to restore monitoring; preferences retained", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
