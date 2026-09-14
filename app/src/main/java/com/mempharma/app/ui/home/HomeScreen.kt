package com.mempharma.app.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mempharma.app.R
import com.mempharma.app.data.local.entity.Medication
import com.mempharma.app.data.scheduler.AlarmActions
import com.mempharma.app.data.scheduler.AlarmRingerService
import com.mempharma.app.data.scheduler.Notifications
import com.mempharma.app.ui.components.EmptyState
import com.mempharma.app.ui.components.MedicationAvatar
import com.mempharma.app.ui.components.RefillDialog
import com.mempharma.app.ui.components.StatusPill
import com.mempharma.app.ui.theme.Dimens
import com.mempharma.app.util.TimeFormat
import com.mempharma.app.util.rememberAppLocale
import com.mempharma.app.util.unitLabelFor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HomeScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val refillTarget by viewModel.refillTarget.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL)
    ) {
        item { Header(state.now) }

        if (state.overdueCount > 0) {
            item { OverdueBanner(state.overdueCount) }
        }

        if (state.cards.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.AddCircle,
                    title = stringResource(R.string.home_empty_title),
                    subtitle = stringResource(R.string.home_empty_subtitle)
                )
            }
        }

        items(state.cards, key = { it.med.id }) { card ->
            DoseCard(
                card = card,
                onEdit = onEdit,
                onRefill = { viewModel.startRefill(card.med) },
                onTake = { occurrence ->
                    viewModel.takeDose(card.med, occurrence)
                    stopActiveAlarm(context, occurrence, closeAlarmScreen = true)
                },
                onMute = { occurrence ->
                    viewModel.muteDose(card.med, occurrence)
                    stopActiveAlarm(context, occurrence, closeAlarmScreen = false)
                }
            )
        }

        item { AddMedicineButton(onAdd) }
    }

    refillTarget?.let { target ->
        RefillDialog(
            med = target.med,
            initialAmount = target.initialAmount,
            onConfirm = viewModel::confirmRefill,
            onDismiss = viewModel::cancelRefill
        )
    }
}

/**
 * Stop any currently ringing alarm after an in-app answer.
 * @param closeAlarmScreen true only when the dose was confirmed taken — that is
 * the sole condition under which the full-screen alarm may be dismissed.
 */
private fun stopActiveAlarm(
    context: android.content.Context,
    occurrence: Long,
    closeAlarmScreen: Boolean
) {
    Notifications.dismiss(context, occurrence)
    AlarmRingerService.stop(context)
    if (closeAlarmScreen) {
        runCatching {
            context.sendBroadcast(android.content.Intent(AlarmActions.ACTION_ALARM_FINISH))
        }
    }
}

@Composable
private fun Header(now: Long) {
    val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
    val greeting = stringResource(
        when {
            hour < 12 -> R.string.home_greeting_morning
            hour < 18 -> R.string.home_greeting_afternoon
            else -> R.string.home_greeting_evening
        }
    )
    val date = TimeFormat.formatDay(now, rememberAppLocale())

    Column {
        Text(text = greeting, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Text(
            text = date,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OverdueBanner(count: Int) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(Dimens.SpaceL),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = scheme.onErrorContainer,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(Dimens.SpaceM))
            Text(
                text = pluralStringResource(R.plurals.home_doses_due, count, count),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AddMedicineButton(onAdd: () -> Unit) {
    Button(
        onClick = onAdd,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.PrimaryActionMinHeight),
        contentPadding = PaddingValues(Dimens.SpaceL)
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(Dimens.SpaceS))
        Text(
            stringResource(R.string.common_add_medicine),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun DoseCard(
    card: HomeMedCard,
    onEdit: (Long) -> Unit,
    onRefill: () -> Unit,
    onTake: (Long) -> Unit,
    onMute: (Long) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val med = card.med
    val pending = card.nextPending
    val locale = rememberAppLocale()

    val resolvedToday = card.allResolvedToday && card.hasSlotsToday
    val containerColor = when {
        card.isDueNow -> scheme.errorContainer
        resolvedToday -> scheme.tertiaryContainer
        else -> scheme.surface
    }
    val contentColor = when {
        card.isDueNow -> scheme.onErrorContainer
        resolvedToday -> scheme.onTertiaryContainer
        else -> scheme.onSurface
    }
    // A solid secondary role rather than a faded copy of the main colour: at 80%
    // opacity the supporting line dropped below accessible contrast, which matters
    // most for exactly the people this app is for.
    val secondaryContent = when {
        card.isDueNow -> scheme.onErrorContainer
        resolvedToday -> scheme.onTertiaryContainer
        else -> scheme.onSurfaceVariant
    }

    Card(
        onClick = { onEdit(med.id) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (containerColor == scheme.surface) {
            BorderStroke(1.dp, scheme.outlineVariant)
        } else null
    ) {
        Column(
            modifier = Modifier.padding(Dimens.SpaceL),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MedicationAvatar(name = med.name, colorIndex = med.colorIndex)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = med.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = contentColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.common_dose_detail,
                            med.doseQuantity,
                            unitLabelFor(med.unitLabel, med.doseQuantity),
                            startedOnLabel(med.startDateEpochDay)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryContent
                    )
                }
            }

            // Stock sits on its own line: translated stock labels are long, and
            // sharing a row with the name squeezed both until one was cropped.
            StockPill(med)

            // The big, single "what do I do next" line.
            when {
                pending != null -> {
                    Text(
                        text = if (pending.overdue) {
                            stringResource(
                                R.string.home_due_now,
                                TimeFormat.formatTime(pending.occurrence, locale)
                            )
                        } else {
                            stringResource(
                                R.string.home_next_dose,
                                TimeFormat.formatTime(pending.occurrence, locale)
                            )
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onTake(pending.occurrence) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 60.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = scheme.tertiary,
                                contentColor = scheme.onTertiary
                            )
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(Modifier.width(Dimens.SpaceS))
                            Text(
                                stringResource(R.string.home_take),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        OutlinedButton(
                            onClick = { onMute(pending.occurrence) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 60.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = secondaryContent
                            )
                        ) {
                            Text(
                                stringResource(R.string.home_not_now),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                resolvedToday -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(Dimens.SpaceS))
                        Text(
                            text = stringResource(R.string.home_all_done),
                            style = MaterialTheme.typography.titleMedium,
                            color = contentColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                else -> {
                    Text(
                        text = stringResource(R.string.home_no_reminder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = secondaryContent
                    )
                }
            }

            // Refilling is always one tap away, whether the bottle is empty or not.
            OutlinedButton(
                onClick = onRefill,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.ControlMinHeight)
            ) {
                Text(
                    stringResource(R.string.common_refill),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

/** Small pill showing remaining stock; turns amber/green/red appropriately. */
@Composable
private fun StockPill(med: Medication) {
    val scheme = MaterialTheme.colorScheme
    val (label, container, content) = when {
        med.isOut -> Triple(
            stringResource(R.string.home_stock_out),
            scheme.errorContainer,
            scheme.onErrorContainer
        )
        med.isLow -> Triple(
            stringResource(R.string.home_stock_low, med.quantity, unitLabelFor(med.unitLabel, med.quantity)),
            scheme.tertiaryContainer,
            scheme.onTertiaryContainer
        )
        else -> Triple(
            stringResource(R.string.home_stock_normal, med.quantity, unitLabelFor(med.unitLabel, med.quantity)),
            scheme.surfaceVariant,
            scheme.onSurfaceVariant
        )
    }
    StatusPill(text = label, container = container, content = content)
}

/** Friendly "Started …" label for the trace line on each card. */
@Composable
private fun startedOnLabel(epochDay: Long): String {
    val days = LocalDate.now().toEpochDay() - epochDay
    return when {
        days < 1 -> stringResource(R.string.time_started_today)
        days == 1L -> stringResource(R.string.time_started_yesterday)
        else -> stringResource(
            R.string.time_started_on,
            TimeFormat.formatDate(epochDay, rememberAppLocale())
        )
    }
}
