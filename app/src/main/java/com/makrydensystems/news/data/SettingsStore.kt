package com.makrydensystems.news.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "starbrook_news_settings",
)

/**
 * Settings (DataStore Preferences). One ZIP key serves BOTH weather and
 * local news — they are the same setting. ZIP defaults to null: no ZIP is
 * ever prefilled (never 73120, never OKC); no ZIP means prompt, no video.
 * Notification defaults: breaking on, everything else off.
 */
class SettingsStore(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    // A corrupt prefs file degrades to defaults instead of crashing the app.
    private val prefsFlow: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val editionFlow: Flow<String> =
        prefsFlow.map { it[Keys.EDITION] ?: DEFAULT_EDITION }

    val sectionsFlow: Flow<Set<String>> =
        prefsFlow.map { it[Keys.SECTIONS] ?: ALL_SECTIONS }

    val zipFlow: Flow<String?> =
        prefsFlow.map { it[Keys.ZIP] }

    val notifyBreakingFlow: Flow<Boolean> =
        prefsFlow.map { it[Keys.NOTIFY_BREAKING] ?: true }

    val notifyMorningFlow: Flow<Boolean> =
        prefsFlow.map { it[Keys.NOTIFY_MORNING] ?: false }

    val notifyEveningFlow: Flow<Boolean> =
        prefsFlow.map { it[Keys.NOTIFY_EVENING] ?: false }

    val notifyWeatherFlow: Flow<Boolean> =
        prefsFlow.map { it[Keys.NOTIFY_WEATHER] ?: false }

    suspend fun setEdition(edition: String) {
        dataStore.edit { it[Keys.EDITION] = edition }
    }

    suspend fun setSectionEnabled(section: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.SECTIONS] ?: ALL_SECTIONS
            prefs[Keys.SECTIONS] = if (enabled) current + section else current - section
        }
    }

    suspend fun setZip(zip: String?) {
        dataStore.edit { prefs ->
            if (zip.isNullOrBlank()) prefs.remove(Keys.ZIP) else prefs[Keys.ZIP] = zip.trim()
        }
    }

    suspend fun setNotifyBreaking(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFY_BREAKING] = enabled }
    }

    suspend fun setNotifyMorning(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFY_MORNING] = enabled }
    }

    suspend fun setNotifyEvening(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFY_EVENING] = enabled }
    }

    suspend fun setNotifyWeather(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFY_WEATHER] = enabled }
    }

    private object Keys {
        val EDITION = stringPreferencesKey("edition")
        val SECTIONS = stringSetPreferencesKey("sections")
        val ZIP = stringPreferencesKey("zip")
        val NOTIFY_BREAKING = booleanPreferencesKey("notify_breaking")
        val NOTIFY_MORNING = booleanPreferencesKey("notify_morning")
        val NOTIFY_EVENING = booleanPreferencesKey("notify_evening")
        val NOTIFY_WEATHER = booleanPreferencesKey("notify_weather")
    }

    companion object {
        const val DEFAULT_EDITION = "morning"
        val ALL_SECTIONS: Set<String> =
            setOf("national", "local", "tech", "business", "culture")
    }
}
