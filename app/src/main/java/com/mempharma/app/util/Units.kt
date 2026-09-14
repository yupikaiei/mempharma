package com.mempharma.app.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import com.mempharma.app.R
import java.util.Locale

/**
 * Turns the stored per-medicine unit label into something that reads properly
 * next to a number.
 *
 * The label is free text the person can edit ("comprimidos", "gotas", "cápsulas"),
 * so it cannot be a plain `plurals` resource. But *most* medicines keep the
 * default we suggested, and the default used to be the literal string
 * `"comprimido(s)"` — which is what produced sentences like
 * *"Tome 1 comprimido(s)"* and *"12 comprimido(s) no frasco"*.
 *
 * So the default is treated as a marker: when the stored label is one we
 * recognise as a default (in any language, including values stored by older
 * installs), we render the *currently displayed* language's proper singular or
 * plural instead. Anything else is the person's own wording and is printed
 * verbatim — we never second-guess a custom label.
 */
object Units {

    /**
     * Every value that means "the default pill noun", lower-cased. Covers the
     * current defaults and the legacy parenthesised form so medicines added by
     * older versions render correctly without any database migration.
     */
    val DEFAULT_LABELS: Set<String> = setOf(
        "comprimido",
        "comprimidos",
        "comprimido(s)",
        "pill",
        "pills",
        "pill(s)"
    )

    /** True when [storedLabel] is blank or is one of the known defaults. */
    fun isDefaultLabel(storedLabel: String?): Boolean {
        val normalized = storedLabel?.trim()?.lowercase(Locale.ROOT) ?: return true
        return normalized.isEmpty() || normalized in DEFAULT_LABELS
    }
}

/** The localized noun for a single pill / several pills. */
@Composable
@ReadOnlyComposable
fun pillNoun(count: Int): String = pluralStringResource(R.plurals.unit_pill, count)

/**
 * The label to show next to [count] in Compose: the person's own wording when
 * they customized it, otherwise the localized "comprimido"/"comprimidos".
 */
@Composable
@ReadOnlyComposable
fun unitLabelFor(storedLabel: String?, count: Int): String =
    if (Units.isDefaultLabel(storedLabel)) pillNoun(count) else storedLabel!!.trim()

/**
 * Non-Compose equivalent, for notifications, audit notes and the automated
 * texts, which build their wording outside a composition.
 */
fun unitLabelFor(context: Context, storedLabel: String?, count: Int): String =
    if (Units.isDefaultLabel(storedLabel)) {
        context.resources.getQuantityString(R.plurals.unit_pill, count, count)
    } else {
        storedLabel!!.trim()
    }
