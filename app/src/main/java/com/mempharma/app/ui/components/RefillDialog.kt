package com.mempharma.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mempharma.app.data.local.entity.Medication

/**
 * Amount pre-filled the very first time a medicine is refilled, before the
 * person has ever told us how many they usually add. Matches the default
 * "pills in the bottle" used when a medicine is first added, so the wording and
 * the number stay consistent across the app.
 */
const val DEFAULT_REFILL_AMOUNT = 30

/** A medicine queued for a quick refill, plus the amount to pre-fill in the dialog. */
data class RefillTarget(
    val med: Medication,
    val initialAmount: Int
)

/**
 * A small, big-touch-target dialog that lets someone add pills back into a
 * medicine's bottle without opening the full edit screen.
 *
 * @param med           the medicine being refilled (used for name, stock and unit).
 * @param initialAmount amount pre-filled from the last refill (or a default).
 * @param onConfirm     called with the chosen positive amount.
 * @param onDismiss     called when the person cancels or taps outside.
 */
@Composable
fun RefillDialog(
    med: Medication,
    initialAmount: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var amountText by remember(med.id) {
        mutableStateOf(initialAmount.coerceAtLeast(1).toString())
    }
    val amount = amountText.toIntOrNull() ?: 0
    val newTotal = med.quantity + amount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Refill ${med.name}",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "${med.quantity} ${med.unitLabel} left in the bottle.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant
                )
                Text(
                    text = "How many are you adding?",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { amountText = (amount - 1).coerceAtLeast(1).toString() },
                        modifier = Modifier
                            .width(72.dp)
                            .height(56.dp)
                    ) {
                        Text("−", style = MaterialTheme.typography.headlineSmall)
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { v -> amountText = v.filter(Char::isDigit).take(5) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedButton(
                        onClick = { amountText = (amount + 1).toString() },
                        modifier = Modifier
                            .width(72.dp)
                            .height(56.dp)
                    ) {
                        Text("+", style = MaterialTheme.typography.headlineSmall)
                    }
                }
                Text(
                    text = "New total: $newTotal ${med.unitLabel}",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(amount) },
                enabled = amount >= 1,
                modifier = Modifier.height(56.dp)
            ) {
                Text("Add to bottle", style = MaterialTheme.typography.titleMedium)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(56.dp)
            ) {
                Text("Cancel", style = MaterialTheme.typography.titleMedium)
            }
        }
    )
}
