package com.im_atp.volthalt

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BatteryService : Service() {

    companion object {
        const val ACTION_STOP_SERVICE             = "STOP_SERVICE"
        const val ACTION_STOP_ALARM               = "STOP_ALARM"
        const val ACTION_STOP_MAX_ALARM_FROM_TILE = "STOP_MAX_ALARM_FROM_TILE"
        const val ACTION_ALARM_STOPPED            = "com.im_atp.volthalt.ALARM_STOPPED"
        const val EXTRA_ALARM_TYPE                = "alarm_type"
        const val ALARM_TYPE_MAX                  = "max_battery"
        const val ALARM_TYPE_LOW                  = "low_battery"

        private const val SERVICE_CHANNEL_ID = "BatteryServiceChannel"
        private const val SERVICE_NOTIF_ID   = 1
        private const val ALARM_CHANNEL_ID   = "BatteryAlarmChannel"
        private const val ALARM_NOTIF_ID     = 2
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var alarmPlayer: AlarmPlayer

    // Both flags are only ever touched on the main thread (batteryReceiver +
    // onStartCommand both run there), so no synchronisation is needed.
    private var isMaxAlarmPlaying = false
    private var isLowAlarmPlaying = false
    private var isMaxAlarmTriggered = false
    private var isLowAlarmTriggered = false

    // In-memory preference cache — kept in sync by lightweight collectors below.
    // Using cached values in the battery receiver avoids DataStore reads on
    // every battery broadcast, which can fire several times per minute.
    private var maxEnabled   = false
    private var maxTarget    = 80
    private var maxSoundType = "ringtone"
    private var maxTtsText   = "Battery charged"
    private var lowEnabled   = false
    private var lowTarget    = 20
    private var lowSoundType = "ringtone"
    private var lowTtsText   = "Low battery"
    private var settingsReady = false
    private var stopping = false
    private var latestBattery: Pair<Int, Boolean>? = null
    private var settingsJob: Job? = null
    private var alarmJob: Job? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return

            val level  = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale  = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            if (level < 0 || scale <= 0) return

            val pct        = (level * 100) / scale
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL

            latestBattery = pct to isCharging
            if (settingsReady && !stopping) checkBatteryLevel(pct, isCharging)
        }
    }

    private fun isScreenUnlocked(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isInteractive = pm?.isInteractive ?: false
        val isKeyguardLocked = km?.isKeyguardLocked ?: false
        return isInteractive && !isKeyguardLocked
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Locking/turning off the screen acts as acknowledgement silence:
                    // stops audio and notification immediately, while retaining trigger locks
                    // so the alarm does not repeat upon next unlock.
                    if (isMaxAlarmPlaying || isLowAlarmPlaying) {
                        silenceAlarms()
                    }
                }
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    if (settingsReady && !stopping && isScreenUnlocked()) {
                        latestBattery?.let { (level, charging) -> checkBatteryLevel(level, charging) }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(applicationContext)
        alarmPlayer        = AlarmPlayer(applicationContext)
        createNotificationChannels()

        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenStateReceiver, screenFilter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote before any asynchronous DataStore work, including cold notification actions.
        startForeground(SERVICE_NOTIF_ID, createServiceNotification("Monitoring battery level…"))
        when (intent?.action) {

            // User hit "Stop Monitoring" — disable both alarms and kill the service.
            ACTION_STOP_SERVICE -> {
                stopping = true
                stopAllAlarms()
                serviceScope.launch {
                    try {
                        // One durable transaction BEFORE onDestroy can cancel serviceScope.
                        preferencesManager.disableAllAlarms()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("VoltHaltMonitoring", "Unable to persist Stop Monitoring", e)
                        stopping = false
                        observeSettings()
                    }
                }
                return START_NOT_STICKY
            }

            // Quick tile toggled the max-battery alarm off.
            // If the low-battery alarm is still active we keep the service running.
            ACTION_STOP_MAX_ALARM_FROM_TILE -> {
                stopMaxAlarm()
            }

            // Silence the alarm but keep the service monitoring.
            ACTION_STOP_ALARM -> {
                silenceAlarms()
            }
        }

        observeSettings()
        return START_STICKY
    }

    private fun observeSettings() {
        if (settingsJob != null) return
        settingsJob = serviceScope.launch {
            try {
                preferencesManager.monitoringSettingsFlow.collect { settings ->
                    maxEnabled = settings.maxEnabled
                    lowEnabled = settings.lowEnabled
                    maxTarget = settings.maxTarget
                    lowTarget = settings.lowTarget
                    maxSoundType = settings.maxSoundType
                    lowSoundType = settings.lowSoundType
                    maxTtsText = settings.maxTtsText
                    lowTtsText = settings.lowTtsText
                    settingsReady = true
                    if (!stopping) {
                        if (!settings.enabled) {
                            stopAllAlarms()
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        } else {
                            latestBattery?.let { (level, charging) -> checkBatteryLevel(level, charging) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("VoltHaltMonitoring", "Unable to load monitoring settings", e)
                stopAllAlarms()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    // Evaluates the current battery state against stored thresholds.
    // Uses edge-triggered semantics with an acknowledged state lock:
    // alerts fire once upon entering the condition and do not re-fire on
    // subsequent broadcasts (e.g. voltage/temperature changes) until reset.
    private fun checkBatteryLevel(currentLevel: Int, isCharging: Boolean) {
        val maxConditionMet = maxEnabled && isCharging && currentLevel >= maxTarget
        val lowConditionMet = lowEnabled && !isCharging && currentLevel <= lowTarget

        // Reset the trigger lock once the condition clears:
        // For Max: unplugging or dropping below target allows a future charge to alert.
        if (!maxConditionMet) {
            stopMaxAlarm()
            isMaxAlarmTriggered = false
        }
        // For Low: plugging in or rising above target allows a future discharge to alert.
        if (!lowConditionMet) {
            stopLowAlarm()
            isLowAlarmTriggered = false
        }

        // Do not alert while the screen is off or locked.
        // Trigger locks are NOT consumed while locked, allowing the alarm to fire
        // immediately upon the user unlocking the screen.
        if (!isScreenUnlocked()) {
            return
        }

        // Fire only on edge transition into the alarm condition.
        if (maxConditionMet && !isMaxAlarmTriggered) {
            isMaxAlarmTriggered = true
            isMaxAlarmPlaying = true
            alarmJob = serviceScope.launch { startAlarm(ALARM_TYPE_MAX) }
        }

        if (lowConditionMet && !isLowAlarmTriggered) {
            isLowAlarmTriggered = true
            isLowAlarmPlaying = true
            alarmJob = serviceScope.launch { startAlarm(ALARM_TYPE_LOW) }
        }
    }

    // Reads the remaining alarm preferences (volume, vibration, ringtone) from DataStore
    // and hands off to AlarmPlayer. DataStore suspends for IO; playback and stop
    // stay on the main dispatcher so a late read cannot restart a stopped alarm.
    private suspend fun startAlarm(type: String) {
        if (type == ALARM_TYPE_MAX) {
            val vibration   = preferencesManager.vibrationEnabledFlow.first()
            val volume      = preferencesManager.alarmVolumeFlow.first()
            if (maxSoundType == "tts") {
                alarmPlayer.playTts(maxTtsText, vibration, volume)
            } else {
                val ringtoneUri = preferencesManager.ringtoneUriFlow.first()
                alarmPlayer.play(ringtoneUri, vibration, volume)
            }
        } else {
            val vibration   = preferencesManager.lowVibrationEnabledFlow.first()
            val volume      = preferencesManager.lowAlarmVolumeFlow.first()
            if (lowSoundType == "tts") {
                alarmPlayer.playTts(lowTtsText, vibration, volume)
            } else {
                val ringtoneUri = preferencesManager.lowRingtoneUriFlow.first()
                alarmPlayer.play(ringtoneUri, vibration, volume)
            }
        }
        showAlarmNotification(type)
    }

    private fun showAlarmNotification(type: String) {
        val alarmIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra(EXTRA_ALARM_TYPE, type)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPending = PendingIntent.getActivity(
            this, 3, alarmIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPending = PendingIntent.getService(
            this, 4,
            Intent(this, BatteryService::class.java).apply { action = ACTION_STOP_ALARM },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (type == ALARM_TYPE_MAX) "⚡ Max Battery Reached!" else "🪫 Low Battery Warning!"
        val body  = if (type == ALARM_TYPE_MAX)
            "Battery has hit your target. Unplug now."
        else
            "Battery is critically low. Please charge."

        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setContentIntent(fullScreenPending)
            .addAction(0, "Stop Alarm", stopPending)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(ALARM_NOTIF_ID, notification)
    }

    private fun stopMaxAlarm() {
        if (!isMaxAlarmPlaying) return
        isMaxAlarmPlaying = false
        if (!isLowAlarmPlaying) {
            alarmJob?.cancel()
            alarmPlayer.stop()
            cancelAlarmNotification()
        }
        broadcastAlarmStopped()
    }

    private fun stopLowAlarm() {
        if (!isLowAlarmPlaying) return
        isLowAlarmPlaying = false
        if (!isMaxAlarmPlaying) {
            alarmJob?.cancel()
            alarmPlayer.stop()
            cancelAlarmNotification()
        }
        broadcastAlarmStopped()
    }

    // Silences current sound and notifications while retaining trigger locks.
    // Subsequent broadcasts within the same condition will not re-alert.
    private fun silenceAlarms() {
        alarmJob?.cancel()
        isMaxAlarmPlaying = false
        isLowAlarmPlaying = false
        alarmPlayer.stop()
        cancelAlarmNotification()
        broadcastAlarmStopped()
    }

    // Fully stops all alarms and resets trigger locks (e.g. when monitoring stops or both alarms disabled).
    fun stopAllAlarms() {
        silenceAlarms()
        isMaxAlarmTriggered = false
        isLowAlarmTriggered = false
    }

    private fun cancelAlarmNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ALARM_NOTIF_ID)
    }

    private fun broadcastAlarmStopped() {
        sendBroadcast(Intent(ACTION_ALARM_STOPPED).setPackage(packageName))
    }

    private fun createServiceNotification(contentText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, BatteryService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle("VoltHalt")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setContentIntent(openIntent)
            .addAction(0, "Stop Monitoring", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID, "Battery Monitoring", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification while VoltHalt is monitoring battery."
            }

            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID, "Battery Alarm", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description          = "Shown when a battery alarm fires."
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(false)
                setBypassDnd(true)
            }

            nm.createNotificationChannel(serviceChannel)
            nm.createNotificationChannel(alarmChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(screenStateReceiver) } catch (_: Exception) {}
        serviceScope.cancel()
        stopAllAlarms()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
