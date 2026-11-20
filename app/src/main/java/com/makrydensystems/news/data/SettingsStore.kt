package com.makrydensystems.news.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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

    // --- Portal Hub (Concept B) ---
    // Newsroom email prefs — the news_subs row (portal news.js). These
    // are the EMAIL edition prefs, deliberately separate from the
    // notify_* toggles above (those drive FCM subscriptions, another
    // agent's area). subscribed is the master gate: off means no edition
    // goes out even though the per-edition choices stay stored.
    val newsSubscribedFlow: Flow<Boolean> =
        prefsFlow.map { it[Keys.NEWS_SUBSCRIBED] ?: true }
    val prefMorningFlow: Flow<Boolean> =
        prefsFlow.map { it[Keys.PREF_MORNING] ?: true }
    val prefEveningFlow: Flow<Boolean> =
        prefsFlow.map { it[Keys.PREF_EVENING] ?: true }
    val prefBreakingFlow: Flow<Boolean> =
        prefsFlow.map { it[Keys.PREF_BREAKING] ?: true }

    // Portal session — mirrors the web portal's mk-portal-session
    // (portal.js takeTokens): access + refresh tokens, expiry epoch ms,
    // email, and the "keep me signed in" choice. Stored in DataStore
    // (app-private, same trust level as the web's localStorage). The
    // access token is only ever sent to Supabase, never logged.
    val portalSessionFlow: Flow<PortalSession?> = prefsFlow.map { prefs ->
        val access = prefs[Keys.SESSION_ACCESS]
        val refresh = prefs[Keys.SESSION_REFRESH]
        if (access.isNullOrBlank() || refresh.isNullOrBlank()) {
            null
        } else {
            PortalSession(
                accessToken = access,
                refreshToken = refresh,
                expiresAtMs = prefs[Keys.SESSION_EXP] ?: 0L,
                email = prefs[Keys.SESSION_EMAIL].orEmpty(),
                keepSignedIn = prefs[Keys.SESSION_REMEMBER] ?: true,
            )
        }
    }

    // First-run coach mark over the Portal star: false until the user has
    // dismissed it once; then it never shows again.
    val portalHintSeenFlow: Flow<Boolean> =
        prefsFlow.map { it[Keys.PORTAL_HINT_SEEN] ?: false }

    // Stable per-install device id for Shelf sync (shelf_items.device_id).
    // Generated once, then kept — never the advertising id, never reset
    // by sign-out.
    val deviceIdFlow: Flow<String> = prefsFlow.map { prefs ->
        prefs[Keys.DEVICE_ID] ?: ""
    }

    suspend fun setNewsSubscribed(enabled: Boolean) {
        dataStore.edit { it[Keys.NEWS_SUBSCRIBED] = enabled }
    }

    suspend fun setPrefMorning(enabled: Boolean) {
        dataStore.edit { it[Keys.PREF_MORNING] = enabled }
    }

    suspend fun setPrefEvening(enabled: Boolean) {
        dataStore.edit { it[Keys.PREF_EVENING] = enabled }
    }

    suspend fun setPrefBreaking(enabled: Boolean) {
        dataStore.edit { it[Keys.PREF_BREAKING] = enabled }
    }

    suspend fun savePortalSession(session: PortalSession) {
        dataStore.edit { prefs ->
            prefs[Keys.SESSION_ACCESS] = session.accessToken
            prefs[Keys.SESSION_REFRESH] = session.refreshToken
            prefs[Keys.SESSION_EXP] = session.expiresAtMs
            prefs[Keys.SESSION_EMAIL] = session.email
            prefs[Keys.SESSION_REMEMBER] = session.keepSignedIn
        }
    }

    suspend fun clearPortalSession() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.SESSION_ACCESS)
            prefs.remove(Keys.SESSION_REFRESH)
            prefs.remove(Keys.SESSION_EXP)
            prefs.remove(Keys.SESSION_EMAIL)
            prefs.remove(Keys.SESSION_REMEMBER)
        }
    }

    suspend fun setPortalHintSeen() {
        dataStore.edit { it[Keys.PORTAL_HINT_SEEN] = true }
    }

    /** Returns the existing device id, generating and storing one first. */
    suspend fun deviceId(): String {
        var current = ""
        dataStore.edit { prefs ->
            current = prefs[Keys.DEVICE_ID]
                ?: java.util.UUID.randomUUID().toString().also { prefs[Keys.DEVICE_ID] = it }
        }
        return current
    }

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

    // Weather cache (Fix 4): the last fetched WeatherBundle + radar
    // frames + video flag, as one JSON blob, so the Weather screen can
    // render instantly on the next open while the network refreshes.
    suspend fun saveWeatherCache(json: String) {
        dataStore.edit { it[Keys.WEATHER_CACHE] = json }
    }

    suspend fun cachedWeatherJson(): String? {
        return prefsFlow.first()[Keys.WEATHER_CACHE]
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
        // Portal Hub (Concept B).
        val NEWS_SUBSCRIBED = booleanPreferencesKey("news_subscribed")
        val PREF_MORNING = booleanPreferencesKey("news_pref_morning")
        val PREF_EVENING = booleanPreferencesKey("news_pref_evening")
        val PREF_BREAKING = booleanPreferencesKey("news_pref_breaking")
        val SESSION_ACCESS = stringPreferencesKey("portal_session_access")
        val SESSION_REFRESH = stringPreferencesKey("portal_session_refresh")
        val SESSION_EXP = longPreferencesKey("portal_session_exp")
        val SESSION_EMAIL = stringPreferencesKey("portal_session_email")
        val SESSION_REMEMBER = booleanPreferencesKey("portal_session_remember")
        val PORTAL_HINT_SEEN = booleanPreferencesKey("portal_hint_seen")
        val DEVICE_ID = stringPreferencesKey("portal_device_id")
        val WEATHER_CACHE = stringPreferencesKey("weather_cache_json")
    }

    companion object {
        const val DEFAULT_EDITION = "morning"
        val ALL_SECTIONS: Set<String> =
            setOf("national", "local", "tech", "business", "culture")
    }
}

/**
 * A portal session, mirroring the web portal's mk-portal-session
 * (portal.js takeTokens). [expiresAtMs] is epoch ms; a token is treated
 * as stale 60s before expiry so refresh happens before any authed call
 * would fail on it.
 */
data class PortalSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMs: Long,
    val email: String,
    val keepSignedIn: Boolean,
) {
    val isExpired: Boolean get() = System.currentTimeMillis() + 60_000L >= expiresAtMs
}
