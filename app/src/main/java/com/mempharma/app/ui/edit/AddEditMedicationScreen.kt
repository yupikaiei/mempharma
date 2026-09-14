package com.mempharma.app.ui.edit

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mempharma.app.R
import com.mempharma.app.ui.components.MedPalette
import com.mempharma.app.ui.theme.Dimens
import com.mempharma.app.util.TimeFormat
import com.mempharma.app.util.rememberAppLocale
import java.time.LocalTime

@Composable
fun AddEditMedicationScreen(
    medId: Long,
    onDone: () -> Unit
) {
    val viewModel: AddEditMedViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.finished, state.deleted) {
        if (state.finished || state.deleted) onDone()
    }

    fun showTimePicker() {
        val cal = java.util.Calendar.getInstance()
        TimePickerDialog(
            context,
            { _, hour, minute -> viewModel.addCustomTime(hour, minute) },
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            DateFormat.is24HourFormat(context)
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // Header: back, title, delete (when editing).
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Text(
                text = stringResource(
                    if (state.isEditing) R.string.edit_title_edit else R.string.edit_title_add
                ),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            if (state.isEditing) {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = scheme.error
                    )
                }
            }
        }

        // 1. Name
        SectionLabel(stringResource(R.string.edit_label_name))
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::updateName,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = {
                Text(
                    stringResource(R.string.edit_placeholder_name),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        )
        Spacer(Modifier.height(20.dp))

        // 2. Dose amount
        SectionLabel(stringResource(R.string.edit_label_dose))
        OutlinedTextField(
            value = state.doseQuantity,
            onValueChange = viewModel::updateDoseQuantity,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(20.dp))

        // 3. Stock
        SectionLabel(stringResource(R.string.edit_label_stock))
        OutlinedTextField(
            value = state.quantity,
            onValueChange = viewModel::updateQuantity,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(20.dp))

        // 4. Unit
        SectionLabel(stringResource(R.string.edit_label_unit))
        OutlinedTextField(
            value = state.unitLabel,
            onValueChange = viewModel::updateUnit,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(20.dp))

        // 4b. Refill warning level (used by the optional "text my family member" alerts).
        SectionLabel(stringResource(R.string.edit_label_low_stock))
        OutlinedTextField(
            value = state.lowStockThreshold,
            onValueChange = viewModel::updateLowStockThreshold,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.edit_low_stock_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        // 5. Colour
        SectionLabel(stringResource(R.string.edit_label_color))
        MedPalette.listColors().chunked(4).forEach { rowColors ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowColors.forEach { color ->
                    val colorIndex = MedPalette.listColors().indexOf(color)
                    ColorDot(
                        color = color,
                        selected = state.colorIndex == colorIndex,
                        label = stringResource(R.string.edit_color_option, colorIndex + 1),
                        onClick = { viewModel.updateColor(colorIndex) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(8.dp))

        // 6. Schedule
        SectionLabel(stringResource(R.string.edit_label_schedule))
        Text(
            text = stringResource(R.string.edit_schedule_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        TimePresets.presets.chunked(2).forEach { rowPresets ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowPresets.forEach { preset ->
                    TimeToggleButton(
                        label = stringResource(preset.labelRes),
                        selected = preset.minuteOfDay in state.selectedTimes,
                        onClick = { viewModel.toggleTime(preset.minuteOfDay) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Any extra (non-preset) times added with the picker.
        val customMinutes = state.selectedTimes
            .filterNot { m -> TimePresets.presets.any { it.minuteOfDay == m } }
            .sorted()
        if (customMinutes.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                customMinutes.forEach { minutes ->
                    CustomTimeChip(minutes, onClick = { viewModel.toggleTime(minutes) })
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlinedButton2(text = stringResource(R.string.edit_add_time), onClick = ::showTimePicker)
        Spacer(Modifier.height(24.dp))

        state.error?.let { error ->
            Text(
                text = stringResource(error),
                color = scheme.error,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
        }

        // 7. Save
        Button(
            onClick = viewModel::save,
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.PrimaryActionMinHeight),
            contentPadding = PaddingValues(Dimens.SpaceL)
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = scheme.onPrimary,
                    strokeWidth = 3.dp
                )
            } else {
                Text(stringResource(R.string.edit_save), style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.edit_delete_title, state.name)) },
            text = { Text(stringResource(R.string.edit_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete()
                    }
                ) { Text(stringResource(R.string.common_delete), color = scheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun ColorDot(
    color: Color,
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clickable(onClick = onClick)
            // Without this a screen reader announced eight identical "button"s.
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .border(
                width = if (selected) 4.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(46.dp).background(color, CircleShape)) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun TimeToggleButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = Dimens.ControlMinHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) scheme.secondaryContainer else scheme.surfaceVariant,
            contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant
        )
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CustomTimeChip(minutes: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val label = TimeFormat.formatLocalTime(
        LocalTime.of(minutes / 60, minutes % 60),
        rememberAppLocale()
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(scheme.surfaceVariant, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = Dimens.SpaceXs, top = Dimens.SpaceXs, bottom = Dimens.SpaceXs)
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        // 48dp, not 32dp: this is the only way to remove a time and it used to be
        // the smallest target in the app.
        IconButton(onClick = onClick, modifier = Modifier.size(Dimens.MinTouchTarget)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.edit_remove_time, label),
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun OutlinedButton2(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.ControlMinHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
