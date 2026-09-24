package com.im_atp.volthalt

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.BatteryManager
import android.os.Looper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
@LooperMode(LooperMode.Mode.PAUSED)
class MonitoringLifecycleTest {
    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail(message)
    }

    @Test fun persistedFlagsDriveBootAndServiceLifecycle() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = PreferencesManager(app)
        val shadowApp = shadowOf(app)
        val notifications = shadowOf(app.getSystemService(NotificationManager::class.java))

        // Exercise the real manifest receiver and DataStore, without opening any Activity.
        for ((max, low) in listOf(false to false, true to false, false to true, true to true)) {
            runBlocking {
                prefs.setAlarmEnabled(max)
                prefs.setLowAlarmEnabled(low)
            }
            shadowApp.clearStartedServices()
            while (shadowApp.nextStoppedService != null) { /* drain */ }
            app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(app.packageName))
            awaitCondition("Boot did not reconcile max=$max low=$low") {
                if (max || low) shadowApp.peekNextStartedService() != null
                else shadowApp.nextStoppedService != null
            }
            if (max || low) {
                assertEquals(BatteryService::class.java.name, shadowApp.nextStartedService.component?.className)
            } else {
                assertNull(shadowApp.nextStartedService)
            }
        }

        // Low-only cold start: the initial sticky battery arrives before settings are loaded.
        runBlocking {
            prefs.setAlarmEnabled(false)
            prefs.setLowAlarmEnabled(true)
            prefs.setLowTargetPercentage(20)
            prefs.setLowAlarmVolume(0)
            prefs.setLowVibrationEnabled(false)
        }
        @Suppress("DEPRECATION")
        app.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 10)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
        })
        val controller = Robolectric.buildService(BatteryService::class.java).create()
        val service = controller.get()
        assertEquals(Service.START_STICKY, service.onStartCommand(null, 0, 1))
        awaitCondition("Low alarm missed the initial sticky battery state") {
            notifications.getNotification(2) != null
        }
        assertNotNull(notifications.getNotification(1))
        assertFalse(shadowOf(service).isStoppedBySelf)

        // Silencing must retain sticky recovery and the foreground monitor.
        assertEquals(Service.START_STICKY, service.onStartCommand(
            Intent().setAction(BatteryService.ACTION_STOP_ALARM), 0, 2
        ))
        assertNull(notifications.getNotification(2))
        assertNotNull(notifications.getNotification(1))

        // Turning the last alarm off stops the existing service without a new battery event.
        runBlocking { prefs.setLowAlarmEnabled(false) }
        awaitCondition("Both disabled must stop the monitor") { shadowOf(service).isStoppedBySelf }
        controller.destroy()
        assertNull(notifications.getNotification(1))
        assertNull(notifications.getNotification(2))

        // A sticky null-intent recreation must re-read disk and stop when both are disabled.
        val disabled = Robolectric.buildService(BatteryService::class.java).create()
        disabled.get().onStartCommand(null, 0, 1)
        awaitCondition("Disabled sticky restart kept monitoring") { shadowOf(disabled.get()).isStoppedBySelf }
        disabled.destroy()

        // Stop Monitoring is durable before self-stop, preserving non-toggle settings.
        runBlocking { prefs.setAlarmEnabled(true); prefs.setLowAlarmEnabled(true) }
        val stopping = Robolectric.buildService(BatteryService::class.java).create()
        assertEquals(Service.START_NOT_STICKY, stopping.get().onStartCommand(
            Intent().setAction(BatteryService.ACTION_STOP_SERVICE), 0, 1
        ))
        awaitCondition("Stop Monitoring never completed") { shadowOf(stopping.get()).isStoppedBySelf }
        stopping.destroy()
        runBlocking {
            assertFalse(prefs.monitoringSettingsFlow.first().enabled)
            assertEquals(20, prefs.lowTargetPercentageFlow.first())
        }
        shadowApp.clearStartedServices()
        while (shadowApp.nextStoppedService != null) { /* drain */ }
        app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(app.packageName))
        awaitCondition("Boot after Stop Monitoring did not finish") { shadowApp.nextStoppedService != null }
        assertNull(shadowApp.nextStartedService)

        // Package replacement follows the same low-only recovery path; random broadcasts do not.
        runBlocking { prefs.setLowAlarmEnabled(true) }
        app.sendBroadcast(Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setPackage(app.packageName))
        awaitCondition("Package update did not restore low-only monitoring") { shadowApp.peekNextStartedService() != null }
        shadowApp.clearStartedServices()
        BootReceiver().onReceive(app, Intent("unrelated.action"))
        assertNull(shadowApp.nextStartedService)
        runBlocking { prefs.disableAllAlarms() }
    }
}
