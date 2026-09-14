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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mempharma.app.R
import com.mempharma.app.ui.components.EmptyState
import com.mempharma.app.ui.components.MedicationAvatar
import com.mempharma.app.ui.components.StatusPill
import com.mempharma.app.ui.theme.Dimens
import com.mempharma.app.util.TimeFormat
import com.mempharma.app.util.rememberAppLocale
import java.io.File
import java.time.LocalDate

private enum class HistoryFilter(@StringRes val labelRes: Int, val predicate: (HistoryRow) -> Boolean) {
    All(R.string.history_filter_all, { true }),
    Taken(R.string.history_filter_taken, { it.action == "TAKEN" }),
    Muted(R.string.history_filter_muted, { it.action == "MUTED" }),
    Missed(R.string.history_filter_missed, { it.action == "MISSED" }),
    Refill(R.string.history_filter_refills, { it.action == "REFILLED" })
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
                Text(
                    stringResource(R.string.history_title),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = stringResource(R.string.history_subtitle),
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
                    contentDescription = stringResource(R.string.history_export),
                    tint = if (rows.isNotEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                )
            }
        }

        // Filters. Chips rather than filled buttons: they are the standard Material
        // control for filtering, and they are compact enough that the longer
        // Portuguese labels ("Reabastecimentos") still reach the edge of the
        // scrollable row instead of hiding off-screen.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
        ) {
            HistoryFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = { Text(stringResource(f.labelRes)) },
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)
                )
            }
        }

        val filtered = rows.filter(filter.predicate)

        if (filtered.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.List,
                title = stringResource(R.string.history_title),
                subtitle = stringResource(R.string.history_empty)
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
    val locale = rememberAppLocale()
    val displayName = row.medName.ifBlank { stringResource(R.string.history_unknown_medicine) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            MedicationAvatar(
                name = displayName,
                colorIndex = row.colorIndex,
                size = Dimens.AvatarSmall
            )
            Spacer(Modifier.width(Dimens.SpaceM))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)
            ) {
                val (pillLabel, pillContainer, pillContent) = actionPill(row.action)
                // The action pill shares the first line with the name, but the name
                // keeps a guaranteed share of the width. The longest translated
                // action ("Reabastecimento") used to squeeze it down to a sliver.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Dimens.SpaceS))
                    StatusPill(text = pillLabel, container = pillContainer, content = pillContent)
                }
                val detail = buildString {
                    append(TimeFormat.formatTime(row.atEpoch, locale))
                    row.scheduledEpoch?.let {
                        append("  ")
                        append(
                            stringResource(
                                R.string.history_due,
                                TimeFormat.formatTime(it, locale)
                            )
                        )
                    }
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
        }
    }
}

@Composable
private fun actionPill(action: String): Triple<String, Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (action) {
        "TAKEN" -> Triple(
            stringResource(R.string.history_action_taken),
            scheme.tertiaryContainer,
            scheme.onTertiaryContainer
        )
        "MUTED" -> Triple(
            stringResource(R.string.history_action_muted),
            scheme.secondaryContainer,
            scheme.onSecondaryContainer
        )
        "MISSED" -> Triple(
            stringResource(R.string.history_action_missed),
            scheme.errorContainer,
            scheme.onErrorContainer
        )
        "REFILLED" -> Triple(
            stringResource(R.string.history_action_refill),
            scheme.primaryContainer,
            scheme.onPrimaryContainer
        )
        else -> Triple(action, scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
}

@Composable
private fun dayLabel(epochDay: Long): String {
    val today = LocalDate.now()
    val date = LocalDate.ofEpochDay(epochDay)
    return when (date) {
        today -> stringResource(R.string.time_today)
        today.minusDays(1) -> stringResource(R.string.time_yesterday)
        else -> TimeFormat.formatWeekdayDate(epochDay, rememberAppLocale())
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
    val chooser = android.content.Intent.createChooser(
        send,
        context.getString(R.string.common_share_medicine_history)
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
