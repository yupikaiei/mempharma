package com.mempharma.app.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mempharma.app.ui.components.MedicationAvatar
import com.mempharma.app.ui.components.StatusPill
import com.mempharma.app.util.TimeFormat
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class HistoryFilter(val label: String, val predicate: (HistoryRow) -> Boolean) {
    All("All", { true }),
    Taken("Taken", { it.action == "TAKEN" }),
    Muted("Muted", { it.action == "MUTED" }),
    Missed("Missed", { it.action == "MISSED" }),
    Refill("Refills", { it.action == "REFILLED" })
}

@Composable
fun HistoryScreen() {
    val viewModel: HistoryViewModel = hiltViewModel()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var filter by remember { mutableStateOf(HistoryFilter.All) }

    LaunchedEffect(viewModel) {
        viewModel.exportFile.collect { file ->
            shareCsv(context, file)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("History", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = "Every action is tracked on this device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { viewModel.export(context) },
                enabled = rows.isNotEmpty()
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = "Export history",
                    tint = if (rows.isNotEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                )
            }
        }

        // Filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HistoryFilter.entries.forEach { f ->
                val selected = filter == f
                Button(
                    onClick = { filter = f },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(f.label, style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        val filtered = rows.filter(filter.predicate)

        if (filtered.isEmpty()) {
            Text(
                text = "Nothing recorded yet.\nTap \"I took it\" or \"Not now\" on a reminder and it will appear here.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // rows are newest-first, so grouping preserves descending day order.
                filtered.groupBy { it.dayEpoch }.forEach { (dayEpoch, dayRows) ->
                    item(key = "day-$dayEpoch") {
                        Text(
                            text = dayLabel(dayEpoch),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(dayRows, key = { "row-${it.atEpoch}-${it.medicationId}" }) { row ->
                        HistoryRowCard(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRowCard(row: HistoryRow) {
    val scheme = MaterialTheme.colorScheme
    val pill = actionPill(row.action)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (pillLabel, pillContainer, pillContent) = actionPill(row.action)
            MedicationAvatar(name = row.medName, colorIndex = row.colorIndex, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.medName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val detail = buildString {
                    append(TimeFormat.formatTime(row.atEpoch))
                    row.scheduledLabel?.let { append("  •  due $it") }
                }
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
                row.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(text = pillLabel, container = pillContainer, content = pillContent)
        }
    }
}

@Composable
private fun actionPill(action: String): Triple<String, androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    val scheme = MaterialTheme.colorScheme
    return when (action) {
        "TAKEN" -> Triple("Taken", scheme.tertiaryContainer, scheme.onTertiaryContainer)
        "MUTED" -> Triple("Muted", scheme.secondaryContainer, scheme.onSecondaryContainer)
        "MISSED" -> Triple("Missed", scheme.errorContainer, scheme.onErrorContainer)
        "REFILLED" -> Triple("Refill", scheme.primaryContainer, scheme.onPrimaryContainer)
        else -> Triple(action, scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
}

private fun dayLabel(epochDay: Long): String {
    val date = LocalDate.ofEpochDay(epochDay)
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
    }
}

private fun shareCsv(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = android.content.Intent.createChooser(send, "Share medicine history")
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
