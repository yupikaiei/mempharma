package com.mempharma.app.data.settings

/**
 * Legacy DataStore value that used to mean "no tone at all".
 *
 * The sound picker no longer offers a silent choice: a reminder must always ring,
 * even when the phone is on "silent" or Do Not Disturb, so it behaves like the
 * phone's own Clock alarm. Values stored by older installs are read back as
 * [AlertTone.SystemDefault]; this constant only exists to make that mapping explicit.
 */
const val LEGACY_ALERT_SILENT = "silent"

/**
 * The sound played when a medicine reminder is due, as chosen by the user in
 * Settings. Persisted as a plain string so no schema/migration is involved.
 */
sealed interface AlertTone {
    /** The device's default alarm tone (the out-of-the-box behaviour). */
    data object SystemDefault : AlertTone

    /** A sound the person picked from the device (a content provider URI). */
    data class Custom(val uri: String) : AlertTone
}

/**
 * Map the raw DataStore value to an [AlertTone]:
 *  - blank/absent            -> [AlertTone.SystemDefault]
 *  - [LEGACY_ALERT_SILENT]   -> [AlertTone.SystemDefault] (old "silent" choice)
 *  - anything else           -> [AlertTone.Custom]
 */
fun parseAlertTone(stored: String?): AlertTone = when {
    stored.isNullOrBlank() -> AlertTone.SystemDefault
    stored == LEGACY_ALERT_SILENT -> AlertTone.SystemDefault
    else -> AlertTone.Custom(stored)
}
