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

    @Test fun alarmTriggerLocksPreventDuplicateAlertsAndResetOnConditionClear() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = PreferencesManager(app)
        val nm = app.getSystemService(NotificationManager::class.java)
        val notifications = shadowOf(nm)

        runBlocking {
            prefs.setAlarmEnabled(true)
            prefs.setLowAlarmEnabled(true)
            prefs.setTargetPercentage(80)
            prefs.setLowTargetPercentage(33)
            prefs.setAlarmVolume(0)
            prefs.setLowAlarmVolume(0)
            prefs.setVibrationEnabled(false)
            prefs.setLowVibrationEnabled(false)
        }

        val controller = Robolectric.buildService(BatteryService::class.java).create()
        val service = controller.get()
        service.onStartCommand(null, 0, 1)

        fun sendBattery(level: Int, charging: Boolean) {
            val status = if (charging) BatteryManager.BATTERY_STATUS_CHARGING else BatteryManager.BATTERY_STATUS_DISCHARGING
            @Suppress("DEPRECATION")
            app.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).apply {
                putExtra(BatteryManager.EXTRA_LEVEL, level)
                putExtra(BatteryManager.EXTRA_SCALE, 100)
                putExtra(BatteryManager.EXTRA_STATUS, status)
            })
            shadowOf(Looper.getMainLooper()).idle()
        }

        // Wait until settings are loaded with an initial safe state (50% discharging)
        sendBattery(50, false)
        awaitCondition("Initial settings not loaded") { notifications.getNotification(1) != null }
        assertNull(notifications.getNotification(2))

        // 1. Drop into low condition: 33% discharging -> Triggers alarm
        sendBattery(33, false)
        awaitCondition("Low alarm failed to trigger on 33%") { notifications.getNotification(2) != null }

        // 2. Subsequent broadcasts within 33% (voltage/temp fluctuations) do not re-trigger
        nm.cancel(2)
        sendBattery(33, false)
        sendBattery(33, false)
        sendBattery(32, false)
        assertNull("Subsequent discharge broadcast must not re-trigger locked alarm", notifications.getNotification(2))

        // 3. Silence / Stop Alarm keeps lock intact
        service.onStartCommand(Intent(this@MonitoringLifecycleTest.javaClass.name).apply {
            action = BatteryService.ACTION_STOP_ALARM
        }, 0, 2)
        sendBattery(32, false)
        sendBattery(31, false)
        assertNull("Broadcast after STOP_ALARM must not re-trigger without charging", notifications.getNotification(2))

        // 4. Plugging in resets the low trigger lock
        sendBattery(31, true)
        sendBattery(35, true)

        // 5. Unplug and drop into low condition again -> Must trigger anew
        sendBattery(35, false)
        assertNull(notifications.getNotification(2))
        sendBattery(33, false)
        awaitCondition("Low alarm failed to re-trigger after charging reset") { notifications.getNotification(2) != null }

        // 6. Max alarm symmetry: charge to 80% -> Triggers Max alarm
        nm.cancel(2)
        sendBattery(80, true)
        awaitCondition("Max alarm failed to trigger on 80%") { notifications.getNotification(2) != null }

        // Subsequent charge broadcasts (81%, 82%) must not re-trigger
        service.onStartCommand(Intent(this@MonitoringLifecycleTest.javaClass.name).apply {
            action = BatteryService.ACTION_STOP_ALARM
        }, 0, 3)
        assertNull(notifications.getNotification(2))
        sendBattery(81, true)
        sendBattery(82, true)
        assertNull("Max alarm re-triggered while continuing to charge", notifications.getNotification(2))

        // Unplugging resets max lock
        sendBattery(82, false)
        sendBattery(80, false)
        // Re-plug at 80% -> Triggers anew
        sendBattery(80, true)
        awaitCondition("Max alarm failed to re-trigger after unplugging reset") { notifications.getNotification(2) != null }

        controller.destroy()
        runBlocking { prefs.disableAllAlarms() }
    }
}
