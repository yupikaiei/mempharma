package com.mempharma.app.ui.settings

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
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
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mempharma.app.BuildConfig
import com.mempharma.app.R
import com.mempharma.app.data.settings.AlertTone
import com.mempharma.app.data.settings.SettingsRepository
import com.mempharma.app.data.settings.parseAlertTone
import com.mempharma.app.data.sms.SmsTestResult
import com.mempharma.app.data.sms.SmsTexts
import com.mempharma.app.domain.SmsStage
import com.mempharma.app.domain.SmsTrigger
import com.mempharma.app.ui.components.CardHeader
import com.mempharma.app.ui.theme.Dimens
import com.mempharma.app.util.AppLocale
import com.mempharma.app.util.pillNoun
import kotlinx.coroutines.delay

private data class FontOption(@StringRes val labelRes: Int, val scale: Float)

private val fontOptions = listOf(
    FontOption(R.string.settings_font_standard, SettingsRepository.FONT_STANDARD),
    FontOption(R.string.settings_font_large, SettingsRepository.FONT_LARGE),
    FontOption(R.string.settings_font_extra, SettingsRepository.FONT_EXTRA_LARGE)
)

private data class LanguageOption(val tag: String, @StringRes val labelRes: Int)

private val languageOptions = listOf(
    LanguageOption(AppLocale.SYSTEM, R.string.settings_language_system),
    LanguageOption(AppLocale.PORTUGUESE, R.string.settings_language_portuguese),
    LanguageOption(AppLocale.ENGLISH, R.string.settings_language_english)
)

@Composable
fun SettingsScreen() {
    val viewModel: SettingsViewModel = hiltViewModel()
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val alertRingtone by viewModel.alertRingtone.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val smsEnabled by viewModel.smsEnabled.collectAsStateWithLifecycle()
    val smsContactName by viewModel.smsContactName.collectAsStateWithLifecycle()
    val smsContactNumber by viewModel.smsContactNumber.collectAsStateWithLifecycle()
    val patientName by viewModel.patientName.collectAsStateWithLifecycle()
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
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() }
        )

        // --- Text size ---
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.settings_text_size_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.settings_text_size_subtitle),
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
                            .padding(vertical = Dimens.SpaceXs)
                            .heightIn(min = Dimens.ControlMinHeight),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) scheme.secondaryContainer else scheme.surfaceVariant,
                            contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant
                        )
                    ) {
                        if (selected) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(option.labelRes), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        // --- Language ---
        LanguageCard(
            selected = language,
            onSelect = { tag ->
                if (tag != language) {
                    viewModel.setLanguage(tag)
                    // Re-create the screen so the new language is applied to
                    // every resource, including the dates we format ourselves.
                    context.findActivity()?.recreate()
                }
            }
        )

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
            patientName = patientName,
            testResult = smsTestResult,
            onEnabledChange = viewModel::setSmsEnabled,
            onContactPicked = viewModel::setSmsContact,
            onClearContact = viewModel::clearSmsContact,
            onPatientNameChange = viewModel::setPatientName,
            onSendTest = viewModel::sendTestSms
        )

        // --- About ---
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Dimens.SpaceL)) {
                CardHeader(Icons.Filled.Info, stringResource(R.string.settings_about_title))
                Spacer(Modifier.height(Dimens.SpaceS))
                Text(
                    stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_about_privacy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Lets the person pin the app's language instead of following the phone.
 * [onSelect] re-creates the screen so the choice applies everywhere at once.
 */
@Composable
private fun LanguageCard(
    selected: String,
    onSelect: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.settings_language_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.settings_language_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            languageOptions.forEach { option ->
                val isSelected = selected == option.tag
                Button(
                    onClick = { onSelect(option.tag) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimens.SpaceXs)
                        .heightIn(min = Dimens.ControlMinHeight),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) scheme.secondaryContainer else scheme.surfaceVariant,
                        contentColor = if (isSelected) scheme.onSecondaryContainer else scheme.onSurfaceVariant
                    )
                ) {
                    if (isSelected) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(option.labelRes), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/**
 * The [Activity] hosting this composition, so a language change can be applied
 * by re-creating it. [LocalContext] can be a wrapper around the activity, hence
 * the unwrapping loop.
 */
private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
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

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    // The alarm must be able to interrupt Do Not Disturb, like the phone's own Clock.
    val dndAccessGranted = notificationManager.isNotificationPolicyAccessGranted()

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
        Column(modifier = Modifier.padding(Dimens.SpaceL)) {
            CardHeader(Icons.Filled.Notifications, stringResource(R.string.settings_reminders_title))
            Spacer(Modifier.height(Dimens.SpaceM))

            Text(
                text = if (notificationsGranted) {
                    stringResource(R.string.settings_notifications_on)
                } else {
                    stringResource(R.string.settings_notifications_off)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.SpaceS)
                        .heightIn(min = Dimens.ControlMinHeight)
                ) { Text(stringResource(R.string.settings_allow_notifications)) }
            }

            Spacer(Modifier.height(Dimens.SpaceS))
            Text(
                text = if (exactAllowed) {
                    stringResource(R.string.settings_precise_on)
                } else {
                    stringResource(R.string.settings_precise_off)
                },
                style = MaterialTheme.typography.bodyLarge
            )
            if (!exactAllowed) {
                Button(
                    onClick = { openSystemSettings() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.SpaceS)
                        .heightIn(min = Dimens.ControlMinHeight)
                ) { Text(stringResource(R.string.settings_allow_precise)) }
            }

            Spacer(Modifier.height(Dimens.SpaceS))
            Text(
                text = if (dndAccessGranted) {
                    stringResource(R.string.settings_dnd_on)
                } else {
                    stringResource(R.string.settings_dnd_off)
                },
                style = MaterialTheme.typography.bodyLarge
            )
            if (!dndAccessGranted) {
                Button(
                    onClick = {
                        settingsLauncher.launch(
                            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.SpaceS)
                        .heightIn(min = Dimens.ControlMinHeight)
                ) { Text(stringResource(R.string.settings_allow_dnd)) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Spacer(Modifier.height(Dimens.SpaceM))
                Text(
                    stringResource(R.string.settings_fullscreen_hint),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.SpaceS)
                        .heightIn(min = Dimens.ControlMinHeight)
                ) { Text(stringResource(R.string.settings_open_notification_settings)) }
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
        // A silent choice is no longer offered, so a null URI means "system default".
        onSelected(picked?.toString() ?: "")
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
            // No silent choice: a reminder must always ring (see [AlertTone]).
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TITLE,
                context.getString(R.string.settings_alert_sound_title)
            )
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, tone.toPlayableUri())
        }
        runCatching { picker.launch(intent) }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dimens.SpaceL)) {
            CardHeader(Icons.Filled.Notifications, stringResource(R.string.settings_alert_sound_title))
            Spacer(Modifier.height(Dimens.SpaceS))
            Text(
                stringResource(R.string.settings_alert_sound_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Dimens.SpaceM))
            Text(
                stringResource(R.string.settings_alert_sound_current, displayName),
                style = MaterialTheme.typography.bodyLarge
            )

            Button(
                onClick = { openPicker() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.SpaceS)
                    .heightIn(min = Dimens.ControlMinHeight)
            ) { Text(stringResource(R.string.settings_choose_sound)) }

            OutlinedButton(
                onClick = {
                    if (previewing) {
                        stopPreview()
                    } else {
                        preview.value = playPreview(context, tone)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.SpaceS)
                    .heightIn(min = Dimens.ControlMinHeight)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (previewing) R.string.common_stop else R.string.common_preview
                    )
                )
            }
        }
    }
}

/** Human-readable name for the current choice. */
private fun toneDisplayName(context: Context, tone: AlertTone): String = when (tone) {
    AlertTone.SystemDefault -> context.getString(R.string.settings_sound_default)
    is AlertTone.Custom -> runCatching {
        RingtoneManager.getRingtone(context, Uri.parse(tone.uri))?.getTitle(context)
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: context.getString(R.string.settings_sound_custom)
}

/** Map the choice to a device URI. */
private fun AlertTone.toPlayableUri(): Uri = when (this) {
    AlertTone.SystemDefault -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    is AlertTone.Custom -> Uri.parse(uri)
}

/** Play a one-shot sample of the chosen tone; returns the playing ringtone. */
private fun playPreview(context: Context, tone: AlertTone): Ringtone? {
    val uri = tone.toPlayableUri()
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
    patientName: String,
    testResult: SmsTestResult?,
    onEnabledChange: (Boolean) -> Unit,
    onContactPicked: (String, String) -> Unit,
    onClearContact: () -> Unit,
    onPatientNameChange: (String) -> Unit,
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

    // Kept locally while typing so every keystroke does not round-trip through
    // DataStore (which would move the cursor); saved when focus is lost.
    var typedName by remember(patientName) { mutableStateOf(patientName) }
    val focusManager = LocalFocusManager.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dimens.SpaceL)) {
            CardHeader(Icons.Filled.Person, stringResource(R.string.settings_sms_title))
            Spacer(Modifier.height(Dimens.SpaceS))
            Text(
                stringResource(R.string.settings_sms_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Dimens.SpaceM))

            OutlinedTextField(
                value = typedName,
                onValueChange = { typedName = it.take(40) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focus ->
                        if (!focus.isFocused && typedName.trim() != patientName) {
                            onPatientNameChange(typedName)
                        }
                    },
                singleLine = true,
                label = { Text(stringResource(R.string.settings_sms_patient_name)) },
                placeholder = { Text(stringResource(R.string.settings_sms_patient_placeholder)) },
                supportingText = { Text(stringResource(R.string.settings_sms_patient_support)) },
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onPatientNameChange(typedName)
                        focusManager.clearFocus()
                    }
                )
            )

            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.settings_sms_send_toggle),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }

            Spacer(Modifier.height(Dimens.SpaceS))
            if (permissionGranted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(Dimens.SpaceS))
                    Text(
                        stringResource(R.string.settings_sms_allowed),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Text(
                    stringResource(R.string.settings_sms_permission_needed),
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.SEND_SMS) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.SpaceS)
                        .heightIn(min = Dimens.ControlMinHeight)
                ) { Text(stringResource(R.string.settings_sms_allow)) }
            }

            Spacer(Modifier.height(Dimens.SpaceM))
            if (contactNumber.isNotBlank()) {
                Text(
                    stringResource(R.string.settings_sms_sending_to),
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
                            .heightIn(min = Dimens.MinTouchTarget)
                    ) { Text(stringResource(R.string.common_change)) }
                    OutlinedButton(
                        onClick = onClearContact,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = Dimens.MinTouchTarget)
                    ) { Text(stringResource(R.string.common_remove)) }
                }
            } else {
                Button(
                    onClick = { chooseContact() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.ControlMinHeight)
                ) { Text(stringResource(R.string.common_choose_contact)) }
                TextButton(
                    onClick = { typingNumber = !typingNumber },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            if (typingNumber) R.string.common_cancel
                            else R.string.common_type_number_instead
                        )
                    )
                }
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
                        Text(
                            stringResource(R.string.settings_sms_number_placeholder),
                            style = MaterialTheme.typography.bodyLarge
                        )
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
                        .padding(top = Dimens.SpaceS)
                        .heightIn(min = Dimens.ControlMinHeight)
                ) { Text(stringResource(R.string.common_save_number)) }
            }

            if (contactNumber.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.settings_sms_preview_caption),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
                Text(
                    SmsTrigger.buildMessage(
                        medicineName = stringResource(R.string.settings_sms_sample_medicine),
                        quantity = 3,
                        unitLabel = pillNoun(3),
                        stage = SmsStage.LOW,
                        patientName = typedName,
                        templates = SmsTexts.templates(context)
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
                OutlinedButton(
                    onClick = onSendTest,
                    enabled = permissionGranted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.SpaceS)
                        .heightIn(min = Dimens.ControlMinHeight)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_sms_test_button))
                }
                testResult?.let { result ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when (result) {
                            SmsTestResult.Sent -> stringResource(R.string.settings_sms_result_sent)
                            SmsTestResult.NoContact -> stringResource(R.string.settings_sms_result_no_contact)
                            SmsTestResult.NoPermission ->
                                stringResource(R.string.settings_sms_result_no_permission)
                            SmsTestResult.Failed ->
                                stringResource(R.string.settings_sms_result_failed)
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
