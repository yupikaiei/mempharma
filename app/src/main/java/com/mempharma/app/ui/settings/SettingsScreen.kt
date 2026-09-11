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
import android.provider.ContactsContract
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mempharma.app.BuildConfig
import com.mempharma.app.data.settings.ALERT_SILENT
import com.mempharma.app.data.settings.AlertTone
import com.mempharma.app.data.settings.SettingsRepository
import com.mempharma.app.data.settings.parseAlertTone
import com.mempharma.app.data.sms.SmsTestResult
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
    val smsEnabled by viewModel.smsEnabled.collectAsStateWithLifecycle()
    val smsContactName by viewModel.smsContactName.collectAsStateWithLifecycle()
    val smsContactNumber by viewModel.smsContactNumber.collectAsStateWithLifecycle()
    val smsTestResult by viewModel.smsTestResult.collectAsStateWithLifecycle()
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

        // --- Refill texts to a family member ---
        SmsAlertCard(
            enabled = smsEnabled,
            contactName = smsContactName,
            contactNumber = smsContactNumber,
            testResult = smsTestResult,
            onEnabledChange = viewModel::setSmsEnabled,
            onContactPicked = viewModel::setSmsContact,
            onClearContact = viewModel::clearSmsContact,
            onSendTest = viewModel::sendTestSms
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

/**
 * "Text a family member" settings: choose a contact, allow sending texts, and
 * try it out. The chosen contact is told when a medicine is almost finished or
 * has run out (the level itself is set per medicine on the Add/Edit screen).
 */
@Composable
private fun SmsAlertCard(
    enabled: Boolean,
    contactName: String,
    contactNumber: String,
    testResult: SmsTestResult?,
    onEnabledChange: (Boolean) -> Unit,
    onContactPicked: (String, String) -> Unit,
    onClearContact: () -> Unit,
    onSendTest: () -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    // Re-check on every return to the screen (e.g. after changing it in system settings).
    var permissionGranted by remember { mutableStateOf(hasSmsPermission(context)) }
    LifecycleResumeEffect(Unit) {
        permissionGranted = hasSmsPermission(context)
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val row = result.data?.data ?: return@rememberLauncherForActivityResult
        readPickedPhoneNumber(context, row)?.let { (name, number) -> onContactPicked(name, number) }
    }

    fun chooseContact() {
        // The system picker returns one contact's phone row; Android grants
        // temporary access to just that row, so READ_CONTACTS is not needed.
        val intent = Intent(Intent.ACTION_PICK)
            .setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)
        runCatching { contactPicker.launch(intent) }
    }

    var typingNumber by remember { mutableStateOf(false) }
    var typedNumber by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = scheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Text a family member",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "We can text someone when a medicine is almost finished or has run out, " +
                    "so they can pick up a refill. We repeat every 3 days until you record a refill.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Send refill texts",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }

            Spacer(Modifier.height(8.dp))
            if (permissionGranted) {
                Text("Sending texts: allowed ✓", style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    "MemPharma needs permission to send texts. Nothing is sent until you allow it.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.SEND_SMS) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(56.dp)
                ) { Text("Allow sending texts") }
            }

            Spacer(Modifier.height(12.dp))
            if (contactNumber.isNotBlank()) {
                Text(
                    "Sending to:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
                Text(
                    text = if (contactName.isBlank()) contactNumber else "$contactName\n$contactNumber",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Button(
                        onClick = { chooseContact() },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) { Text("Change") }
                    OutlinedButton(
                        onClick = onClearContact,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) { Text("Remove") }
                }
            } else {
                Button(
                    onClick = { chooseContact() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) { Text("Choose contact") }
                TextButton(
                    onClick = { typingNumber = !typingNumber },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (typingNumber) "Cancel" else "Type a number instead") }
            }

            if (typingNumber && contactNumber.isBlank()) {
                OutlinedTextField(
                    value = typedNumber,
                    onValueChange = { typedNumber = it.take(20) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    placeholder = {
                        Text("e.g. 07700 900123", style = MaterialTheme.typography.bodyLarge)
                    }
                )
                Button(
                    onClick = {
                        onContactPicked("", typedNumber.trim())
                        typingNumber = false
                        typedNumber = ""
                    },
                    enabled = typedNumber.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(56.dp)
                ) { Text("Save number") }
            }

            if (contactNumber.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "What a medicine that is almost finished will say:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
                Text(
                    "MemPharma: Blood pressure pill is almost finished — only 3 pill(s) left. " +
                        "Please arrange a refill.",
                    style = MaterialTheme.typography.bodyLarge
                )
                OutlinedButton(
                    onClick = onSendTest,
                    enabled = permissionGranted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(56.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Send a test text")
                }
                testResult?.let { result ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when (result) {
                            SmsTestResult.Sent -> "Test text sent ✓"
                            SmsTestResult.NoContact -> "Choose a contact first."
                            SmsTestResult.NoPermission -> "Allow sending texts first."
                            SmsTestResult.Failed ->
                                "The phone could not send the text. Check the signal and try again."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (result == SmsTestResult.Sent) scheme.primary else scheme.error
                    )
                }
            }
        }
    }
}

/** Whether MemPharma may send texts right now. */
private fun hasSmsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Reads the name and number from the single contact row the system picker
 * returned. Android grants temporary access to that one row, which is why no
 * READ_CONTACTS permission is needed.
 */
private fun readPickedPhoneNumber(context: Context, row: Uri): Pair<String, String>? =
    runCatching {
        context.contentResolver.query(row, PICK_PROJECTION, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIdx = cursor.getColumnIndex(PICK_PROJECTION[0])
            val numberIdx = cursor.getColumnIndex(PICK_PROJECTION[1])
            val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
            val number = if (numberIdx >= 0) cursor.getString(numberIdx).orEmpty().trim() else ""
            if (number.isBlank()) null else name to number
        }
    }.getOrNull()

private val PICK_PROJECTION = arrayOf(
    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
    ContactsContract.CommonDataKinds.Phone.NUMBER
)
