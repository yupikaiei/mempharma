package com.mempharma.app.data.scheduler

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import com.mempharma.app.di.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that makes a reminder behave like a real alarm:
 * it plays the system alarm sound **on a loop** and vibrates continuously until
 * the person acts ("I took it" / "Not now"). Because it is a foreground service
 * it keeps running even if the app or the full-screen activity is closed.
 */
class AlarmRingerService : Service() {

    companion object {
        const val ACTION_START = "com.mempharma.alarm.START"
        const val ACTION_STOP = "com.mempharma.alarm.STOP"

        fun start(context: Context, medId: Long, occurrence: Long) {
            val intent = Intent(context, AlarmRingerService::class.java)
                .setAction(ACTION_START)
                .putExtra(AlarmActions.EXTRA_MED_ID, medId)
                .putExtra(AlarmActions.EXTRA_OCCURRENCE, occurrence)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmRingerService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var alarmJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val medId = intent?.getLongExtra(AlarmActions.EXTRA_MED_ID, -1L) ?: -1L
                val occurrence = intent?.getLongExtra(AlarmActions.EXTRA_OCCURRENCE, -1L) ?: -1L
                if (medId < 0 || occurrence < 0) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startAlarm(medId, occurrence)
                return START_NOT_STICKY
            }
        }
    }

    private fun startAlarm(medId: Long, occurrence: Long) {
        acquireWakeLock()
        alarmJob = scope.launch {
            val med = AppGraph.from(applicationContext)
                .medicationRepository()
                .get(medId) ?: run {
                    stopSelf()
                    return@launch
                }

            // Foreground notification doubles as the full-screen, ongoing alarm
            // indicator that cannot be swiped away.
            val notification = Notifications.buildAlarmNotification(applicationContext, med, occurrence)
            startForeground(Notifications.notificationId(occurrence), notification)

            startRinging()
        }
    }

    /** Loop the alarm tone + vibration until the service is stopped. */
    private fun startRinging() {
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(applicationContext, ringtoneUri)
            ?: RingtoneManager.getRingtone(
                applicationContext,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            )
        ringtone?.isLooping = true
        ringtone?.play()

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN)

        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator = v
        if (v?.hasVibrator() == true) {
            try {
                v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 300), 0))
            } catch (_: Exception) {
                // very defensive: if vibration cannot start, the tone still rings
            }
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MemPharma:AlarmRinger"
        ).apply { acquire() }
    }

    private fun stopRinging() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
        (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
            ?.abandonAudioFocus(null)
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    override fun onDestroy() {
        alarmJob?.cancel()
        stopRinging()
        scope.cancel()
        super.onDestroy()
    }
}
