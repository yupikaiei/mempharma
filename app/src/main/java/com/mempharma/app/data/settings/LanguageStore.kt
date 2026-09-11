package com.mempharma.app.data.settings

import android.content.Context
import com.mempharma.app.util.AppLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "mempharma_language"
private const val KEY_LANGUAGE = "language"

/**
 * The language picked in Settings — [AppLocale.SYSTEM], [AppLocale.PORTUGUESE]
 * or [AppLocale.ENGLISH].
 *
 * Deliberately backed by [android.content.SharedPreferences] instead of
 * DataStore: the choice has to be readable **synchronously** while a context is
 * being attached (see [withAppLanguage]), before DataStore's asynchronous flow
 * could ever deliver a value. SharedPreferences answers on the spot, so the
 * chosen language is already in force for the very first frame and for any
 * notification posted by a receiver that starts a fresh process.
 */
@Singleton
class LanguageStore @Inject constructor(
    @ApplicationContext context: Context
) {

    private val preferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(readStoredLanguage(context))

    /** The current choice; always one of [AppLocale.selectableTags]. */
    val language: StateFlow<String> = _language.asStateFlow()

    /** Store a new choice. [withAppLanguage] sees it immediately. */
    fun select(tag: String) {
        val normalised = AppLocale.normalise(tag)
        preferences.edit().putString(KEY_LANGUAGE, normalised).apply()
        _language.value = normalised
    }
}

/**
 * A copy of this context whose resources use the language chosen in Settings.
 *
 * Safe (and cheap) to call anywhere, and safe to call on a context that is
 * already localized. The application and each activity use it while attaching;
 * the few places that resolve user-facing text outside Compose (notification
 * titles and actions, refill texts, audit notes, CSV headers) use it too, so
 * they follow a language change made in the same session.
 */
fun Context.withAppLanguage(): Context =
    AppLocale.localized(this, readStoredLanguage(this))

/**
 * Synchronous read that needs neither Hilt nor a coroutine, so it also works
 * from [android.app.Application.attachBaseContext] and
 * [android.app.Activity.attachBaseContext]. Always returns a language accepted
 * by [AppLocale.normalise].
 */
private fun readStoredLanguage(context: Context): String =
    AppLocale.normalise(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
    )
