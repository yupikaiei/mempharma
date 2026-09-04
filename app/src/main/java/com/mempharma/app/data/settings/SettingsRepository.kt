package com.mempharma.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "mempharma_settings")

/**
 * Lightweight app settings persisted with Jetpack DataStore.
 * Currently used for the global accessibility font-scale so elderly users can
 * make every screen bigger without leaving the app's design system.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        const val FONT_STANDARD = 1.0f
        const val FONT_LARGE = 1.2f
        const val FONT_EXTRA_LARGE = 1.45f
    }

    val fontScale: Flow<Float> = context.dataStore.data
        .map { it[KEY_FONT_SCALE] ?: FONT_STANDARD }

    suspend fun setFontScale(scale: Float) {
        context.dataStore.edit { it[KEY_FONT_SCALE] = scale }
    }
}
