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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mempharma.app.ui.components.MedPalette
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = if (state.isEditing) "Edit medicine" else "Add a medicine",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            if (state.isEditing) {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = scheme.error
                    )
                }
            }
        }

        // 1. Name
        SectionLabel("Medicine name")
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::updateName,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text("e.g. Blood pressure pill", style = MaterialTheme.typography.bodyLarge) }
        )
        Spacer(Modifier.height(20.dp))

        // 2. Dose amount
        SectionLabel("How many pills each time?")
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
        SectionLabel("Pills in the bottle right now")
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
        SectionLabel("What do we call each pill?")
        OutlinedTextField(
            value = state.unitLabel,
            onValueChange = viewModel::updateUnit,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(20.dp))

        // 5. Colour
        SectionLabel("Choose a colour for this medicine")
        MedPalette.listColors().chunked(4).forEach { rowColors ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowColors.forEachIndexed { idx, color ->
                    val colorIndex = MedPalette.listColors().indexOf(color)
                    ColorDot(
                        color = color,
                        selected = state.colorIndex == colorIndex,
                        onClick = { viewModel.updateColor(colorIndex) }
                    )
                    if (idx < rowColors.lastIndex) Spacer(Modifier.width(0.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(8.dp))

        // 6. Schedule
        SectionLabel("When should we remind you?")
        Text(
            text = "Tap the times you take this medicine.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        TimePresets.presets.chunked(2).forEach { rowPresets ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowPresets.forEach { preset ->
                    TimeToggleButton(
                        label = preset.label,
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

        OutlinedButton2(text = "Add another time", onClick = ::showTimePicker)
        Spacer(Modifier.height(24.dp))

        state.error?.let { error ->
            Text(
                text = error,
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
                .height(64.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = scheme.onPrimary,
                    strokeWidth = 3.dp
                )
            } else {
                Text("Save medicine", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${state.name}?") },
            text = { Text("This removes the medicine and its reminder history from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete()
                    }
                ) { Text("Delete", color = scheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
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
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clickable(onClick = onClick)
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
        modifier = modifier.height(56.dp),
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
    val label = LocalTime.of(minutes / 60, minutes % 60)
        .format(DateTimeFormatter.ofPattern("h:mm a"))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(scheme.surfaceVariant, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove $label",
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
        modifier = Modifier.fillMaxWidth().height(56.dp),
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
