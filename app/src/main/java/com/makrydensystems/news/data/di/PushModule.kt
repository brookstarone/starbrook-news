package com.makrydensystems.news.data.di

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Push wiring point — IN-APP notification engine (v1, no Firebase).
 *
 * What this is: the notification toggles in Settings are REAL controls
 * for in-app live refresh. Each toggle gates a polling loop that runs
 * only while its screen's flow is collected (i.e. while the app is open
 * on that screen):
 * - Breaking ON  -> breaking.json re-checked every 5 min; a new breaking
 *   item surfaces in the Home strip (NewsRepository.breakingFlow).
 * - Morning ON  -> edition-morning.json re-checked every 15 min, so the
 *   new edition lands at/after its ~07:00 build without reopening the
 *   app (NewsRepository.editionsFlow("morning")).
 * - Evening ON  -> same for edition-evening.json after its ~16:00 build.
 * - Weather alerts ON -> NWS alerts re-checked every 10 min while the
 *   Weather screen is open (WeatherRepository.alertsFlow).
 * OFF stops its polling: the loop suspends on the gate (zero network)
 * until the toggle comes back ON. Nothing here runs while the app is
 * closed — there is no background work and no push in v1.
 *
 * How the gate gets its value: the single source of truth is the
 * DataStore-backed SettingsStore (notify*Flow, defaults breaking=true,
 * everything else false). SettingsScreen's ViewModel mirrors every
 * toggle change into the StateFlows below, and NewsRepository /
 * WeatherRepository read SettingsStore directly once AppContainer passes
 * it in (constructor arg, nullable so v1 still compiles without it).
 * These in-memory flows are the fallback until that one-line hookup
 * lands — they start at the same defaults as SettingsStore, so a fresh
 * install behaves identically either way.
 *
 * FCM (NOT built — needs Matthew's word): when the Firebase project
 * exists, this object gains the token-registration + topic-subscription
 * calls and the app gains the Firebase Messaging dependency. Topics map
 * 1:1 to the toggles: "breaking", "morning", "evening",
 * "weather-alerts". The project itself is still an open item — WHICH
 * Google account — do NOT create it. Until then there are no Firebase
 * imports anywhere in the app and the build must succeed with no
 * google-services.json present.
 */
object PushModule {

    /** FCM topics, 1:1 with the Settings toggles — used once FCM lands. */
    const val TOPIC_BREAKING = "breaking"
    const val TOPIC_MORNING = "morning"
    const val TOPIC_EVENING = "evening"
    const val TOPIC_WEATHER_ALERTS = "weather-alerts"

    // In-memory mirrors of SettingsStore.notify*Flow. Defaults match the
    // store exactly (breaking on, everything else off).
    private val _breakingEnabled = MutableStateFlow(true)
    val breakingEnabled: StateFlow<Boolean> = _breakingEnabled.asStateFlow()

    private val _morningEnabled = MutableStateFlow(false)
    val morningEnabled: StateFlow<Boolean> = _morningEnabled.asStateFlow()

    private val _eveningEnabled = MutableStateFlow(false)
    val eveningEnabled: StateFlow<Boolean> = _eveningEnabled.asStateFlow()

    private val _weatherAlertsEnabled = MutableStateFlow(false)
    val weatherAlertsEnabled: StateFlow<Boolean> = _weatherAlertsEnabled.asStateFlow()

    fun setBreakingEnabled(enabled: Boolean) {
        _breakingEnabled.value = enabled
    }

    fun setMorningEnabled(enabled: Boolean) {
        _morningEnabled.value = enabled
    }

    fun setEveningEnabled(enabled: Boolean) {
        _eveningEnabled.value = enabled
    }

    fun setWeatherAlertsEnabled(enabled: Boolean) {
        _weatherAlertsEnabled.value = enabled
    }

    /** Sync all four gates at once (SettingsViewModel init). */
    fun updateAll(
        breaking: Boolean,
        morning: Boolean,
        evening: Boolean,
        weatherAlerts: Boolean,
    ) {
        _breakingEnabled.value = breaking
        _morningEnabled.value = morning
        _eveningEnabled.value = evening
        _weatherAlertsEnabled.value = weatherAlerts
    }
}
