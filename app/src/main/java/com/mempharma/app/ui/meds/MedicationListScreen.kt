package com.mempharma.app.ui.meds

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Button
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
import com.mempharma.app.ui.components.EmptyState
import com.mempharma.app.ui.components.MedicationAvatar
import com.mempharma.app.ui.components.RefillDialog
import com.mempharma.app.ui.components.StatusPill
import com.mempharma.app.ui.theme.Dimens
import com.mempharma.app.util.TimeFormat
import com.mempharma.app.util.rememberAppLocale
import com.mempharma.app.util.unitLabelFor
import java.util.Locale

@Composable
fun MedicationListScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val viewModel: MedListViewModel = hiltViewModel()
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val refillTarget by viewModel.refillTarget.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Header(medications.size)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
        ) {
            if (medications.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.AddCircle,
                        title = stringResource(R.string.meds_title),
                        subtitle = stringResource(R.string.meds_empty)
                    )
                }
            }
            items(medications, key = { it.id }) { med ->
                MedicineRow(
                    med = med,
                    onClick = { onEdit(med.id) },
                    onRefill = { viewModel.startRefill(med) }
                )
            }
        }

        Button(
            onClick = onAdd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.ScreenPadding)
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

    refillTarget?.let { target ->
        RefillDialog(
            med = target.med,
            initialAmount = target.initialAmount,
            onConfirm = viewModel::confirmRefill,
            onDismiss = viewModel::cancelRefill
        )
    }
}

@Composable
private fun Header(count: Int) {
    Column(
        modifier = Modifier.padding(
            start = Dimens.ScreenPadding,
            end = Dimens.ScreenPadding,
            top = Dimens.SpaceL
        )
    ) {
        Text(
            stringResource(R.string.meds_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = pluralStringResource(R.plurals.meds_count, count, count),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MedicineRow(
    med: Medication,
    onClick: () -> Unit,
    onRefill: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(Dimens.SpaceL),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MedicationAvatar(name = med.name, colorIndex = med.colorIndex, size = Dimens.AvatarMedium)
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
            ) {
                Text(
                    text = med.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.common_dose_detail,
                        med.doseQuantity,
                        unitLabelFor(med.unitLabel, med.doseQuantity),
                        scheduleLabel(med, rememberAppLocale())
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // Stock and the refill action get their own line. Long translated
                // labels used to sit opposite the medicine name and squeeze it.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StockPill(med)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = onRefill,
                        modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                        contentPadding = PaddingValues(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS)
                    ) {
                        Text(
                            stringResource(R.string.common_refill),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

private fun scheduleLabel(med: Medication, locale: Locale): String =
    med.times.joinToString(" · ") { TimeFormat.formatLocalTime(it, locale) }

@Composable
private fun StockPill(med: Medication) {
    val scheme = MaterialTheme.colorScheme
    val (label, container, content) = when {
        med.isOut -> Triple(
            stringResource(R.string.meds_stock_out),
            scheme.errorContainer,
            scheme.onErrorContainer
        )
        med.isLow -> Triple(
            stringResource(R.string.meds_stock_low, med.quantity, unitLabelFor(med.unitLabel, med.quantity)),
            scheme.tertiaryContainer,
            scheme.onTertiaryContainer
        )
        else -> Triple(
            stringResource(R.string.meds_stock_normal, med.quantity, unitLabelFor(med.unitLabel, med.quantity)),
            scheme.surfaceVariant,
            scheme.onSurfaceVariant
        )
    }
    StatusPill(text = label, container = container, content = content)
}
