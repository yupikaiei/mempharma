package com.mempharma.app.data.scheduler

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import com.mempharma.app.data.settings.AlertTone
import com.mempharma.app.data.settings.parseAlertTone
import com.mempharma.app.di.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
    private var audioFocusRequest: AudioFocusRequest? = null
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
            val graph = AppGraph.from(applicationContext)
            val med = graph.medicationRepository()
                .get(medId) ?: run {
                    stopSelf()
                    return@launch
                }

            // The sound the person chose in Settings (device default when unset).
            val tone = parseAlertTone(graph.settingsRepository().alertRingtone.first())

            // Foreground notification doubles as the full-screen, ongoing alarm
            // indicator that cannot be swiped away.
            val notification = Notifications.buildAlarmNotification(applicationContext, med, occurrence)
            startForeground(Notifications.notificationId(occurrence), notification)

            startRinging(tone)
        }
    }

    /**
     * Loop the chosen alert tone + vibration until the service is stopped.
     *
     * The tone is rendered on the **alarm** stream ([AudioAttributes.USAGE_ALARM]),
     * exactly like the phone's own Clock alarm. That is what keeps a reminder audible
     * when the phone is on "silent" (which only mutes the ringer and notification
     * streams) and when Do Not Disturb is on (the system's "alarms" category is
     * allowed by default).
     */
    private fun startRinging(tone: AlertTone) {
        // Force USAGE_ALARM no matter which sound was picked: a tone chosen from the
        // ringtone/notification lists would otherwise play on a stream that "silent"
        // mode mutes, and the reminder would not be heard.
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val ringtoneUri: Uri = when (tone) {
            AlertTone.SystemDefault -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            is AlertTone.Custom -> Uri.parse(tone.uri)
        }

        // Prefer the chosen sound, then fall back to the default alarm tone and
        // finally the default notification tone so a reminder never rings silent
        // by accident.
        ringtone = (RingtoneManager.getRingtone(applicationContext, ringtoneUri)
            ?: RingtoneManager.getRingtone(
                applicationContext,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            )
            ?: RingtoneManager.getRingtone(
                applicationContext,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ))?.apply {
            // Must be assigned before play(): this decides which stream is used.
            audioAttributes = attributes
            isLooping = true
            play()
        }

        if (ringtone != null) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .build()
            audioFocusRequest = request
            audioManager?.requestAudioFocus(request)
        }

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
        (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.let { audioManager ->
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        }
        audioFocusRequest = null
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
