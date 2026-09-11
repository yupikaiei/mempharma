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
import androidx.compose.runtime.setValue
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
 *  - The alert is persisted, so it is brought back to the front whenever the app
 *    is reopened or the notification is tapped — even after the process was
 *    killed — until the dose is taken.
 *  - "Not now" only silences the ringing (logs a MUTE); it does NOT close the
 *    screen and does not clear the alert — it comes back in the same calm, muted
 *    state until the dose is confirmed.
 *  - The back button is disabled while this screen is up.
 *  - Only a TAKEN confirmation (here, from the notification action, or from the
 *    Today screen) records the dose and dismisses this activity. If more doses
 *    are still waiting, the next alert is shown straight away.
 */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject
    lateinit var medicationRepository: MedicationRepository

    @Inject
    lateinit var trackingRepository: TrackingRepository

    private var medId by mutableStateOf(-1L)
    private var occurrence by mutableStateOf(-1L)

    private val muted = mutableStateOf(false)

    /** Advances past a dose that was confirmed taken elsewhere. */
    private val finishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AlarmActions.ACTION_ALARM_FINISH && !isFinishing) {
                lifecycleScope.launch { advanceToNextPendingOrFinish() }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Alarm-style: wake and cover the lock screen.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguard.requestDismissKeyguard(this, null)

        // Back/gesture must NOT dismiss the alarm before it is confirmed taken.
        onBackPressedDispatcher.addCallback(this) { /* stay on the alarm screen */ }

        loadAlertFromIntent(intent)

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

    /**
     * This activity is single-instance: when another alert arrives (or the app is
     * reopened and brings the pending alert back) Android reuses this screen and
     * calls this instead of creating a new one.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadAlertFromIntent(intent)
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

    /** "✓ I took it" — the only action that closes this screen for this dose. */
    private fun confirmTaken(med: Medication?) {
        if (isFinishing) return
        val takenOccurrence = occurrence
        lifecycleScope.launch {
            if (med != null) {
                trackingRepository.recordTaken(med, takenOccurrence, System.currentTimeMillis())
            }
            AlarmRingerService.stop(this@AlarmActivity)
            Notifications.dismiss(this@AlarmActivity, takenOccurrence)
            // Show the next dose still waiting, if any; otherwise close.
            advanceToNextPendingOrFinish()
        }
    }

    /** "Not now" — silence the ringing only; the screen (and alert) stay. */
    private fun muteRinging(med: Medication?) {
        if (muted.value) return
        val mutedOccurrence = occurrence
        lifecycleScope.launch {
            if (med != null) {
                trackingRepository.recordMuted(med, mutedOccurrence, System.currentTimeMillis())
            }
            AlarmRingerService.stop(this@AlarmActivity)
            muted.value = true
            // Screen may sleep again now the ringing is silenced; it will still
            // be on this screen waiting when the person looks again.
            applyScreenAwake(false)
        }
    }

    /** Show the next alert still waiting for an answer, or close when none left. */
    private suspend fun advanceToNextPendingOrFinish() {
        val next = trackingRepository.pendingAlert()
        when {
            // Nothing left to answer — only now may the screen close.
            next == null -> finish()
            // Our own dose is still waiting (e.g. a different dose was answered
            // elsewhere), so keep this alert on screen.
            next.medicationId == medId && next.occurrence == occurrence -> Unit
            else -> loadAlert(next.medicationId, next.occurrence)
        }
    }

    private fun loadAlertFromIntent(intent: Intent?) {
        loadAlert(
            intent?.getLongExtra(AlarmActions.EXTRA_MED_ID, -1L) ?: -1L,
            intent?.getLongExtra(AlarmActions.EXTRA_OCCURRENCE, -1L) ?: -1L
        )
    }

    /**
     * Points the screen at one dose alarm, restoring its muted state from the
     * audit log so a silenced-but-untaken alert returns in its calm form.
     */
    private fun loadAlert(id: Long, occ: Long) {
        if (id < 0 || occ < 0) {
            finish()
            return
        }
        medId = id
        occurrence = occ
        muted.value = false
        applyScreenAwake(true)

        lifecycleScope.launch {
            // Ignore a stale load if another alert replaced this one meanwhile.
            if (medId != id || occurrence != occ) return@launch
            val isMuted = trackingRepository.isMuted(id, occ)
            muted.value = isMuted
            applyScreenAwake(!isMuted)
        }
    }

    /** Keep the screen awake while the alarm is actively asking for an answer. */
    private fun applyScreenAwake(awake: Boolean) {
        if (awake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
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
