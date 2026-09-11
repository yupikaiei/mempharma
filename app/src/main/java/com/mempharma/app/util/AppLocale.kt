package com.mempharma.app.util

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * MemPharma ships two languages:
 *  - **Portuguese (Portugal)** — the default/fallback, kept in `res/values`.
 *  - **English** — used when the phone itself is set to English (`res/values-en`).
 *
 * Nothing forces a locale, so the *displayed* language and the device locale can
 * differ: a phone set to French has no French resources, so Android falls back to
 * the Portuguese default. Date and time formatting must therefore follow the
 * language that is actually shown, **not** [Locale.getDefault]. [of] mirrors the
 * same fallback decision the resource system makes, so formatted dates always
 * match the visible text.
 */
object AppLocale {

    /** BCP-47 tag of the default (fallback) language: Portuguese from Portugal. */
    const val DEFAULT_LANGUAGE_TAG = "pt-PT"

    /** The device language that has its own resource set. */
    private const val ENGLISH_LANGUAGE = "en"

    fun of(context: Context): Locale = of(context.resources.configuration)

    fun of(configuration: Configuration): Locale =
        if (configuration.locales[0].language == ENGLISH_LANGUAGE) {
            Locale.ENGLISH
        } else {
            Locale.forLanguageTag(DEFAULT_LANGUAGE_TAG)
        }
}

/** The locale of the language currently displayed, for date/time formatting. */
@Composable
@ReadOnlyComposable
fun rememberAppLocale(): Locale = AppLocale.of(LocalConfiguration.current)
