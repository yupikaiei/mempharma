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
 * On top of that the person can pin a language in Settings — "System default",
 * "Português" or "English". The choice is persisted by
 * [com.mempharma.app.data.settings.LanguageStore], and [localized] turns a plain
 * [Context] into one whose resources resolve that language. The application and
 * every activity call it while attaching, so the whole app (screens,
 * notifications and texts) follows the choice from the first frame. With
 * [SYSTEM] nothing is forced and Android falls back exactly as before.
 *
 * Because the displayed language can differ from the device locale (a French
 * phone, an English phone where the person picked Portuguese, ...), date and
 * time formatting must follow the language that is actually shown, **not**
 * [Locale.getDefault]. [of] mirrors the same decision the resource system makes,
 * so formatted dates always match the visible text.
 */
object AppLocale {

    /** Stored preference meaning "follow the phone's own language" (the default). */
    const val SYSTEM = "system"

    /** BCP-47 tag of the Portuguese (Portugal) resources. */
    const val PORTUGUESE = "pt-PT"

    /** BCP-47 tag of the English resources. */
    const val ENGLISH = "en"

    /** BCP-47 tag of the default (fallback) language: Portuguese from Portugal. */
    const val DEFAULT_LANGUAGE_TAG = PORTUGUESE

    /**
     * The languages the person can pick in Settings, in the order they are
     * shown there. [SYSTEM] means "do not force anything".
     */
    val selectableTags = listOf(SYSTEM, PORTUGUESE, ENGLISH)

    /**
     * Normalise any stored value to one of [selectableTags]. Anything missing,
     * blank or unrecognised means [SYSTEM], so a stale or hand-edited preference
     * can never break startup. Both full tags and bare language codes are
     * accepted ("pt", "pt-PT", "PT" all mean [PORTUGUESE]).
     */
    fun normalise(stored: String?): String = when {
        stored.isNullOrBlank() -> SYSTEM
        stored.equals(SYSTEM, ignoreCase = true) -> SYSTEM
        stored.startsWith(ENGLISH, ignoreCase = true) -> ENGLISH
        stored.startsWith("pt", ignoreCase = true) -> PORTUGUESE
        else -> SYSTEM
    }

    /**
     * The BCP-47 tag to force for this stored preference, or null when the
     * phone's own language should be followed ([SYSTEM]).
     */
    fun forcedTag(stored: String?): String? =
        normalise(stored).takeIf { it != SYSTEM }

    /**
     * A copy of [context] whose resources use the language described by
     * [stored] (see [normalise]). For [SYSTEM] — or when nothing forces a
     * language — [context] is returned untouched so the normal resource
     * fallback still applies.
     */
    fun localized(context: Context, stored: String?): Context {
        val tag = forcedTag(stored) ?: return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return context.createConfigurationContext(configuration)
    }

    fun of(context: Context): Locale = of(context.resources.configuration)

    fun of(configuration: Configuration): Locale =
        if (configuration.locales[0].language == ENGLISH) {
            Locale.ENGLISH
        } else {
            Locale.forLanguageTag(DEFAULT_LANGUAGE_TAG)
        }
}

/** The locale of the language currently displayed, for date/time formatting. */
@Composable
@ReadOnlyComposable
fun rememberAppLocale(): Locale = AppLocale.of(LocalConfiguration.current)
