package com.mempharma.app.ui.alarm

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.mempharma.app.data.local.entity.Medication
import com.mempharma.app.data.repo.MedicationRepository
import com.mempharma.app.data.repo.TrackingRepository
import com.mempharma.app.data.scheduler.AlarmActions
import com.mempharma.app.data.scheduler.AlarmRingerService
import com.mempharma.app.data.scheduler.Notifications
import com.mempharma.app.ui.components.StatusPill
import com.mempharma.app.ui.theme.MemPharmaTheme
import com.mempharma.app.util.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Full-screen alarm shown when a dose is due.
 *
 * Behaviour contract: **the screen stays visible until the person confirms they
 * took the medicine by pressing "✓ I took it".**
 *  - "Not now" only silences the ringing (logs a MUTE); it does NOT close the
 *    screen — the alarm keeps waiting in a calm, muted state.
 *  - The back button is disabled while this screen is up.
 *  - Only a TAKEN confirmation (here, from the notification action, or from the
 *    Today screen) records the dose and dismisses this activity.
 */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject
    lateinit var medicationRepository: MedicationRepository

    @Inject
    lateinit var trackingRepository: TrackingRepository

    private var medId: Long = -1L
    private var occurrence: Long = -1L

    private val muted = mutableStateOf(false)

    /** Closes this screen whenever a TAKEN confirmation happens elsewhere. */
    private val finishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AlarmActions.ACTION_ALARM_FINISH) {
                if (!isFinishing) finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        medId = intent?.getLongExtra(AlarmActions.EXTRA_MED_ID, -1L) ?: -1L
        occurrence = intent?.getLongExtra(AlarmActions.EXTRA_OCCURRENCE, -1L) ?: -1L

        // Alarm-style: wake and cover the lock screen; keep screen awake while
        // the alarm is ringing so it cannot be ignored.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguard.requestDismissKeyguard(this, null)

        // Back/gesture must NOT dismiss the alarm before it is confirmed taken.
        onBackPressedDispatcher.addCallback(this) { /* stay on the alarm screen */ }

        setContent {
            MemPharmaTheme(darkTheme = true) {
                val med by remember(medId) {
                    medicationRepository.observe(medId)
                }.collectAsState(initial = null)

                AlarmContent(
                    med = med,
                    occurrence = occurrence,
                    muted = muted.value,
                    onTaken = { confirmTaken(med) },
                    onMute = { muteRinging(med) }
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

    /** "✓ I took it" — the only action that closes this screen. */
    private fun confirmTaken(med: Medication?) {
        if (isFinishing) return
        lifecycleScope.launch {
            if (med != null) {
                trackingRepository.recordTaken(med, occurrence, System.currentTimeMillis())
            }
            AlarmRingerService.stop(this@AlarmActivity)
            Notifications.dismiss(this@AlarmActivity, occurrence)
            finish()
        }
    }

    /** "Not now" — silence the ringing only; the screen stays until confirmed. */
    private fun muteRinging(med: Medication?) {
        if (muted.value) return
        lifecycleScope.launch {
            if (med != null) {
                trackingRepository.recordMuted(med, occurrence, System.currentTimeMillis())
            }
            AlarmRingerService.stop(this@AlarmActivity)
            muted.value = true
            // Screen may sleep again now the ringing is silenced; it will still
            // be on this screen waiting when the person looks again.
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
private fun AlarmContent(
    med: Medication?,
    occurrence: Long,
    muted: Boolean,
    onTaken: () -> Unit,
    onMute: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.5f))

        Icon(
            Icons.Filled.Notifications,
            contentDescription = null,
            tint = if (muted) scheme.onSurfaceVariant else scheme.error,
            modifier = Modifier.size(110.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = when {
                muted && med != null -> "${med.name} — still to take"
                muted -> "Medicine still to take"
                med != null -> "Time for ${med.name}"
                else -> "Time for your medicine"
            },
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

        if (muted) {
            Spacer(Modifier.height(20.dp))
            StatusPill(
                text = "Reminder muted — tap below when you have taken it",
                container = scheme.secondaryContainer,
                content = scheme.onSecondaryContainer
            )
        }

        Spacer(Modifier.weight(0.8f))

        // The confirmation that ends the alarm.
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

        if (!muted) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onMute,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onBackground)
            ) {
                Text("Not now (stop ringing)", style = MaterialTheme.typography.titleLarge)
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "This alert stays on until you confirm the medicine was taken.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
