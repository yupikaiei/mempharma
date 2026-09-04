package com.mempharma.app.data.local.entity

/**
 * The type of user action recorded in the append-only [DoseEvent] audit log.
 * Everything a person does (or that the system infers) is stored as one of these
 * so we can always trace back what happened and when.
 */
enum class DoseAction {
    /** User confirmed they took the scheduled dose (decrements quantity). */
    TAKEN,

    /** User silenced a reminder without taking the dose (no decrement). */
    MUTED,

    /** A reminder elapsed without any action; recorded lazily as overdue. */
    MISSED,

    /** The user added pills back to the stock (quantity increase). */
    REFILLED,

    /** A medication record was created / edited (start-date traceability). */
    EDITED
}
