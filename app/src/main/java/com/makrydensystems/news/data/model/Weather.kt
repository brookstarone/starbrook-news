package com.makrydensystems.news.data.model

/**
 * Weather models — plain Kotlin data classes (built from NWS JSON by
 * WeatherRepository, not decoded directly, so no @Serializable here).
 * NWS (api.weather.gov) is the only forecast source.
 */

// Current conditions, taken from the first hourly forecast period.
data class CurrentConditions(
    val temperatureF: Int,
    // NWS forecast/hourly periods carry no feels-like value; null until a
    // gridpoint apparent-temperature read is wired (see TODO.md).
    val feelsLikeF: Int?,
    val condition: String,
    val shortForecast: String,
    val windMph: Int?,
    val windDirection: String?,
    val humidityPct: Int?,
    val iconUrl: String?,
)

data class HourlyPeriod(
    val startTime: String, // ISO-8601 with offset, as returned by NWS
    val temperatureF: Int,
    val shortForecast: String,
    val isDaytime: Boolean,
)

data class DailyForecast(
    val date: String, // YYYY-MM-DD
    val highF: Int?,
    val lowF: Int?,
    val shortForecast: String,
    val iconUrl: String?,
)

data class WeatherAlert(
    val headline: String,
    val severity: String,
    val event: String,
    val description: String,
    val expires: String?, // ISO-8601, null when NWS omits it
)

data class WeatherBundle(
    val current: CurrentConditions,
    val hourly: List<HourlyPeriod>, // next 24 hours
    val daily: List<DailyForecast>, // next 7 days
    val alerts: List<WeatherAlert>,
)

/**
 * One RainViewer radar frame. The live API keys frames by a path hash
 * ("/v2/radar/<hash>"), NOT by epoch time — the tile URL is built from
 * `path` (verified live 2026-11-19: epoch-pattern tiles return 410,
 * path-pattern tiles return 200). `time` is the frame's epoch seconds,
 * kept for display + animation order.
 */
data class RadarFrame(
    val time: Long,
    val path: String,
)
