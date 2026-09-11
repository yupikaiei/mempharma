package com.mempharma.app.data.settings

/**
 * Raw DataStore value that means "no tone at all".
 * The reminder still vibrates and shows the full-screen alert.
 */
const val ALERT_SILENT = "silent"

/**
 * The sound played when a medicine reminder is due, as chosen by the user in
 * Settings. Persisted as a plain string so no schema/migration is involved.
 */
sealed interface AlertTone {
    /** The device's default alarm tone (the out-of-the-box behaviour). */
    data object SystemDefault : AlertTone

    /** No tone — reminders still vibrate and show the full-screen alert. */
    data object Silent : AlertTone

    /** A sound the person picked from the device (a content provider URI). */
    data class Custom(val uri: String) : AlertTone
}

/**
 * Map the raw DataStore value to an [AlertTone]:
 *  - blank/absent  -> [AlertTone.SystemDefault]
 *  - [ALERT_SILENT] -> [AlertTone.Silent]
 *  - anything else  -> [AlertTone.Custom]
 */
fun parseAlertTone(stored: String?): AlertTone = when {
    stored.isNullOrBlank() -> AlertTone.SystemDefault
    stored == ALERT_SILENT -> AlertTone.Silent
    else -> AlertTone.Custom(stored)
}
