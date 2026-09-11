package com.mempharma.app.util

import android.content.Context
import com.mempharma.app.R
import com.mempharma.app.data.local.entity.DoseAction
import com.mempharma.app.data.local.entity.DoseEvent
import com.mempharma.app.data.local.entity.Medication
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds a CSV "trace" file containing every medicine (with its start date) and
 * the full local event log (taken / muted / missed / refill) with timestamps.
 * This is the "trace it back" export the product needs.
 */
object ExportUtils {

    private val stampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
    private val dateOnly = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Writes the export into `cacheDir/exports/` and returns the [File].
     * Safe to call from a background thread only.
     */
    fun buildCsvFile(
        context: Context,
        meds: List<Medication>,
        events: List<DoseEvent>,
        zone: ZoneId = ZoneId.systemDefault()
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "mempharma-history-${System.currentTimeMillis()}.csv")
        val medsById = meds.associateBy { it.id }

        val sb = StringBuilder()
        // BOM so Excel opens non-ASCII text correctly.
        sb.append("\uFEFF")

        sb.append(context.getString(R.string.export_header)).append('\n')
        sb.append(context.getString(R.string.export_exported)).append(',')
            .append(Instant.now().atZone(zone).format(stampFormatter)).append('\n')
        sb.append('\n')

        // --- Medicines ---
        sb.append(context.getString(R.string.export_medicines_section)).append('\n')
        sb.append(context.getString(R.string.export_medicines_header)).append('\n')
        meds.forEach { med ->
            sb.append(csv(med.name))
                .append(',').append(med.doseQuantity)
                .append(',').append(csv(med.unitLabel))
                .append(',').append(csv(med.timesCsv))
                .append(',').append(med.quantity)
                .append(',').append(med.lowStockThreshold)
                .append(',').append(java.time.LocalDate.ofEpochDay(med.startDateEpochDay).format(dateOnly))
                .append('\n')
        }
        sb.append('\n')

        // --- Event log ---
        sb.append(context.getString(R.string.export_events_section)).append('\n')
        sb.append(context.getString(R.string.export_events_header)).append('\n')
        events
            .sortedBy { it.actionAtEpochMillis }
            .forEach { event ->
                val med = medsById[event.medicationId]
                val scheduled = event.scheduledForEpochMillis
                    ?.let { Instant.ofEpochMilli(it).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm", Locale.US)) }
                    ?: ""
                sb.append(Instant.ofEpochMilli(event.actionAtEpochMillis).atZone(zone).format(stampFormatter))
                    .append(',').append(csv(med?.name ?: context.getString(R.string.export_unknown_medicine, event.medicationId)))
                    .append(',').append(csv(actionLabel(context, event.action)))
                    .append(',').append(csv(scheduled))
                    .append(',').append(csv(event.note ?: ""))
                    .append('\n')
            }

        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    private fun actionLabel(context: Context, action: String): String = when (action) {
        DoseAction.TAKEN.name -> context.getString(R.string.export_action_taken)
        DoseAction.MUTED.name -> context.getString(R.string.export_action_muted)
        DoseAction.MISSED.name -> context.getString(R.string.export_action_missed)
        DoseAction.REFILLED.name -> context.getString(R.string.export_action_refill)
        else -> action
    }

    /** Minimal RFC-4180 style quoting. */
    private fun csv(value: String): String =
        "\"${value.replace("\"", "\"\"")}\""
}
