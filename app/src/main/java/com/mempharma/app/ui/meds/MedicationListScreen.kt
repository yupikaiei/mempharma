package com.mempharma.app.ui.meds

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mempharma.app.R
import com.mempharma.app.data.local.entity.Medication
import com.mempharma.app.ui.components.MedicationAvatar
import com.mempharma.app.ui.components.RefillDialog
import com.mempharma.app.ui.components.StatusPill
import com.mempharma.app.util.TimeFormat
import com.mempharma.app.util.rememberAppLocale
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (medications.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.meds_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 24.dp)
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
                .padding(16.dp)
                .height(64.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.common_add_medicine), style = MaterialTheme.typography.titleMedium)
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
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
        Text(stringResource(R.string.meds_title), style = MaterialTheme.typography.headlineLarge)
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
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MedicationAvatar(name = med.name, colorIndex = med.colorIndex, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = med.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(
                        R.string.common_dose_detail,
                        med.doseQuantity,
                        med.unitLabel,
                        scheduleLabel(med, rememberAppLocale())
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StockPill(med)
                OutlinedButton(
                    onClick = onRefill,
                    modifier = Modifier.height(44.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text(stringResource(R.string.common_refill), style = MaterialTheme.typography.labelLarge)
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
            stringResource(R.string.meds_stock_low, med.quantity),
            scheme.tertiaryContainer,
            scheme.onTertiaryContainer
        )
        else -> Triple(
            stringResource(R.string.meds_stock_normal, med.quantity),
            scheme.surfaceVariant,
            scheme.onSurfaceVariant
        )
    }
    StatusPill(text = label, container = container, content = content)
}
