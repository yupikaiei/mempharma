package com.mempharma.app.ui.alarm

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mempharma.app.data.local.entity.Medication
import com.mempharma.app.data.repo.MedicationRepository
import com.mempharma.app.data.repo.TrackingRepository
import com.mempharma.app.data.scheduler.AlarmActions
import com.mempharma.app.data.scheduler.AlarmRingerService
import com.mempharma.app.data.scheduler.Notifications
import com.mempharma.app.ui.theme.MemPharmaTheme
import com.mempharma.app.util.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

/**
 * The full-screen alarm shown when a dose is due. It fills the whole display,
 * works over the lock screen, and stays until the person answers. Ringing is
 * driven by [AlarmRingerService] so it does not stop on its own; tapping one of
 * the two buttons here answers the alarm (records locally) and stops it.
 */
@AndroidEntryPoint
class AlarmActivity : androidx.activity.ComponentActivity() {

    @Inject
    lateinit var medicationRepository: MedicationRepository

    @Inject
    lateinit var trackingRepository: TrackingRepository

    private var medId: Long = -1L
    private var occurrence: Long = -1L

    private val finishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AlarmActions.ACTION_ALARM_FINISH) {
                finishAndClearTaskIfNeeded()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        medId = intent?.getLongExtra(AlarmActions.EXTRA_MED_ID, -1L) ?: -1L
        occurrence = intent?.getLongExtra(AlarmActions.EXTRA_OCCURRENCE, -1L) ?: -1L

        // Alarm-style behaviour: keep the screen on and wake from lock screen.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguard.requestDismissKeyguard(this, null)

        setContent {
            MemPharmaTheme(darkTheme = true) {
                val med by remember(medId) {
                    medicationRepository.observe(medId)
                }.collectAsState(initial = null)

                AlarmContent(
                    med = med,
                    occurrence = occurrence,
                    onTaken = { answerDose(med, taken = true) },
                    onMute = { answerDose(med, taken = false) },
                    onJustStop = { finishAlarm() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(AlarmActions.ACTION_ALARM_FINISH)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(finishReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(finishReceiver, filter)
            }
        }
    }

    override fun onStop() {
        runCatching { unregisterReceiver(finishReceiver) }
        super.onStop()
    }

    private fun answerDose(med: Medication?, taken: Boolean) {
        lifecycleScope.launch {
            if (med != null) {
                val now = System.currentTimeMillis()
                if (taken) trackingRepository.recordTaken(med, occurrence, now)
                else trackingRepository.recordMuted(med, occurrence, now)
            }
            finishAlarm()
        }
    }

    private fun finishAlarm() {
        AlarmRingerService.stop(this)
        Notifications.dismiss(this, occurrence)
        finish()
    }

    /** finish() from the dynamic broadcast can arrive off the main thread path. */
    private fun finishAndClearTaskIfNeeded() {
        if (isFinishing) return
        finishAlarm()
    }
}

@Composable
private fun AlarmContent(
    med: Medication?,
    occurrence: Long,
    onTaken: () -> Unit,
    onMute: () -> Unit,
    onJustStop: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val pulse by rememberInfiniteTransition(label = "alarm").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alarmPulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.6f))

        Icon(
            Icons.Filled.Notifications,
            contentDescription = null,
            tint = scheme.error,
            modifier = Modifier
                .size(120.dp)
                .padding(24.dp * pulse) // gentle "breathing" motion
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (med != null) "Time for ${med.name}" else "Time for your medicine",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = scheme.onBackground
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = if (med != null) {
                "Take ${med.doseQuantity} ${med.unitLabel}"
            } else {
                "It is time to take your medicine"
            },
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onBackground,
            textAlign = TextAlign.Center
        )

        if (occurrence > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Scheduled at ${TimeFormat.formatTime(occurrence)}",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.weight(0.8f))

        // The two big answers to the alarm.
        Button(
            onClick = onTaken,
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = scheme.tertiary,
                contentColor = scheme.onTertiary
            )
        ) {
            Text("✓  I took it", style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onMute,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onBackground)
        ) {
            Text("Not now", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(12.dp))

        // Small, low-key escape hatch (e.g. wrong/mistaken alarm): stops the
        // ringing without logging anything as taken.
        androidx.compose.material3.TextButton(
            onClick = onJustStop,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Stop this alert", color = scheme.onSurfaceVariant)
        }
    }
}
