package com.mempharma.app.ui.settings

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mempharma.app.BuildConfig
import com.mempharma.app.data.settings.ALERT_SILENT
import com.mempharma.app.data.settings.AlertTone
import com.mempharma.app.data.settings.SettingsRepository
import com.mempharma.app.data.settings.parseAlertTone
import kotlinx.coroutines.delay

private data class FontOption(val label: String, val scale: Float)

private val fontOptions = listOf(
    FontOption("Standard", SettingsRepository.FONT_STANDARD),
    FontOption("Large", SettingsRepository.FONT_LARGE),
    FontOption("Extra large", SettingsRepository.FONT_EXTRA_LARGE)
)

@Composable
fun SettingsScreen() {
    val viewModel: SettingsViewModel = hiltViewModel()
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val alertRingtone by viewModel.alertRingtone.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)

        // --- Text size ---
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Text size", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Make everything bigger if it is hard to read.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                fontOptions.forEach { option ->
                    val selected = fontScale == option.scale
                    Button(
                        onClick = { viewModel.setFontScale(option.scale) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) scheme.secondaryContainer else scheme.surfaceVariant,
                            contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant
                        )
                    ) {
                        if (selected) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(option.label, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        // --- Reminders & alerts ---
        ReminderSettingsCard(context)

        // --- Alert sound ---
        AlertSoundCard(
            selected = alertRingtone,
            onSelected = viewModel::setAlertRingtone
        )

        // --- About ---
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = scheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "MemPharma • version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Everything is stored only on this device. " +
                        "No account is needed and nothing is sent to the internet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReminderSettingsCard(context: Context) {
    val scheme = MaterialTheme.colorScheme
    val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* returning here; app re-schedules reminders on next open */ }

    fun openSystemSettings() {
        val intent = if (Build.VERSION.SDK_INT in Build.VERSION_CODES.S..Build.VERSION_CODES.TIRAMISU) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        }
        settingsLauncher.launch(intent)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Notifications, contentDescription = null, tint = scheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Reminders & alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))

            Text(
                text = if (notificationsGranted) {
                    "Notifications: on ✓"
                } else {
                    "Notifications are turned off — alerts cannot be shown."
                },
                style = MaterialTheme.typography.bodyLarge
            )
            if (!notificationsGranted) {
                Button(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Allow notifications") }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = if (exactAllowed) {
                    "Precise reminders: on ✓"
                } else {
                    "Precise reminders are off. Reminders may be late — please allow them."
                },
                style = MaterialTheme.typography.bodyLarge
            )
            if (!exactAllowed) {
                Button(
                    onClick = { openSystemSettings() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Allow precise reminders") }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "To make reminders cover the whole screen, switch on " +
                        "\u201cFull-screen notifications\u201d for MemPharma in system settings.",
                    style = MaterialTheme.typography.bodyLarge
                )
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Open notification settings") }
            }
        }
    }
}

/**
 * Lets the person pick the reminder tone from the device's own sounds.
 * The choice is stored through [onSelected] and used by the ringer service.
 */
@Composable
private fun AlertSoundCard(
    selected: String,
    onSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    val tone = parseAlertTone(selected)
    val displayName = remember(tone) { toneDisplayName(context, tone) }

    // A short, non-looping sample so the person can hear the choice.
    val preview = remember { mutableStateOf<Ringtone?>(null) }
    val previewing = preview.value != null

    fun stopPreview() {
        preview.value?.stop()
        preview.value = null
    }

    DisposableEffect(Unit) {
        onDispose { preview.value?.stop() }
    }

    // Stop the sample automatically so it never rings on and on.
    LaunchedEffect(previewing) {
        if (previewing) {
            delay(5_000)
            stopPreview()
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val picked: Uri? = result.data?.let { data ->
            IntentCompat.getParcelableExtra(
                data,
                RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                Uri::class.java
            )
        }
        stopPreview()
        // The picker reports "Silent" as a null URI.
        onSelected(picked?.toString() ?: ALERT_SILENT)
    }

    fun openPicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_ALARM or
                    RingtoneManager.TYPE_RINGTONE or
                    RingtoneManager.TYPE_NOTIFICATION
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alert sound")
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, tone.toPlayableUri())
        }
        runCatching { picker.launch(intent) }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Notifications, contentDescription = null, tint = scheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Alert sound",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "The sound that plays when it is time to take a medicine.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text("Current: $displayName", style = MaterialTheme.typography.bodyLarge)

            Button(
                onClick = { openPicker() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(56.dp)
            ) { Text("Choose sound") }

            OutlinedButton(
                onClick = {
                    if (previewing) {
                        stopPreview()
                    } else {
                        preview.value = playPreview(context, tone)
                    }
                },
                enabled = tone != AlertTone.Silent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(56.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (previewing) "Stop" else "Preview")
            }

            if (tone == AlertTone.Silent) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Silent: reminders still vibrate and show the full-screen alert.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Human-readable name for the current choice. */
private fun toneDisplayName(context: Context, tone: AlertTone): String = when (tone) {
    AlertTone.SystemDefault -> "Default (system alarm)"
    AlertTone.Silent -> "Silent"
    is AlertTone.Custom -> runCatching {
        RingtoneManager.getRingtone(context, Uri.parse(tone.uri))?.getTitle(context)
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Custom sound"
}

/** Map the choice to a device URI, or null for [AlertTone.Silent]. */
private fun AlertTone.toPlayableUri(): Uri? = when (this) {
    AlertTone.Silent -> null
    AlertTone.SystemDefault -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    is AlertTone.Custom -> Uri.parse(uri)
}

/** Play a one-shot sample of the chosen tone; returns the playing ringtone. */
private fun playPreview(context: Context, tone: AlertTone): Ringtone? {
    val uri = tone.toPlayableUri() ?: return null
    return runCatching {
        RingtoneManager.getRingtone(context, uri)?.apply {
            isLooping = false
            play()
        }
    }.getOrNull()
}
